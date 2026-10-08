package com.bahceden.catalog;

import com.bahceden.catalog.domain.Product;
import com.bahceden.catalog.domain.ProductPhoto;
import com.bahceden.catalog.domain.ProductStatus;
import com.bahceden.catalog.domain.ProductStatusChange;
import com.bahceden.catalog.domain.ProductTransition;
import com.bahceden.catalog.domain.ProductUnit;
import com.bahceden.catalog.dto.ListingRequest;
import com.bahceden.catalog.dto.SellerListingView;
import com.bahceden.common.money.Money;
import com.bahceden.common.storage.FileStorage;
import com.bahceden.common.text.Slugs;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.common.web.PageResponse;
import com.bahceden.seller.SellerService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Satıcının ilan yönetimi (product-listings spec'i, add-product-listings D1–D4).
 *
 * <p>Her metodun ilk adımı onaylı satıcı kapısıdır ({@link SellerService#requireApprovedSeller},
 * ADR-044): askıdaki satıcının token'ında rol hâlâ dursa bile işlem reddedilir.
 * İkinci adım sahipliktir: ilan bu satıcının kimliğiyle sorgulanır
 * ({@link ProductRepository#findByIdAndSellerId}, ADR-007); başka satıcının
 * ilanı 404 olur.
 *
 * <p>Dosyalar veritabanıyla tutarlı tutulur (ADR-042 deseni): transaction geri
 * alınırsa yazılan dosyalar silinir; silinen fotoğrafın dosyaları ancak
 * transaction başarıyla bittikten sonra silinir.
 */
@Service
public class ListingService {

    private static final Logger log = LoggerFactory.getLogger(ListingService.class);

    /** MVP tek para birimiyle çalışır; satıcı seçmez (design.md varsayımları). */
    private static final Currency CURRENCY = Currency.getInstance("TRY");

    /** Ad yalnızca Latin dışı karakterlerden oluşuyorsa kullanılan taban. */
    private static final String SLUG_FALLBACK = "urun";

    static final int MAX_PAGE_SIZE = 50;

    /** İlan başına fotoğraf üst sınırı (D3); şemadaki {@code position between 0 and 4} ile aynı. */
    static final int MAX_PHOTOS = 5;

    /** İstekte süre gönderilmezse yeni ilanın kargoya verme süresi (D3, ADR-058 olumsuz sonucu). */
    static final int DEFAULT_DISPATCH_DAYS = 2;

    private final ProductRepository products;
    private final ProductPhotoRepository photos;
    private final ProductStatusChangeRepository statusChanges;
    private final ProductMapper mapper;
    private final SellerService sellers;
    private final PhotoProcessor photoProcessor;
    private final FileStorage storage;
    private final Clock clock;

    ListingService(ProductRepository products,
                   ProductPhotoRepository photos,
                   ProductStatusChangeRepository statusChanges,
                   ProductMapper mapper,
                   SellerService sellers,
                   PhotoProcessor photoProcessor,
                   FileStorage storage,
                   Clock clock) {
        this.products = products;
        this.photos = photos;
        this.statusChanges = statusChanges;
        this.mapper = mapper;
        this.sellers = sellers;
        this.photoProcessor = photoProcessor;
        this.storage = storage;
        this.clock = clock;
    }

    /** Yeni ilan {@code DRAFT} açılır; {@code slug} addan üretilir ve sonra değişmez (D2). */
    @Transactional
    public SellerListingView create(long accountId, ListingRequest request) {
        var seller = sellers.requireApprovedSeller(accountId);
        var now = clock.instant();
        var product = products.save(Product.draft(seller, uniqueSlug(request.name()), request.name().trim(),
                description(request), ProductUnit.valueOf(request.unit()),
                Money.of(request.priceAmount(), CURRENCY), request.stockQuantity(),
                request.dispatchDays() == null ? DEFAULT_DISPATCH_DAYS : request.dispatchDays(), now));
        statusChanges.save(ProductStatusChange.record(product.getId(), null, ProductStatus.DRAFT, null, accountId, now));
        log.info("İlan oluşturuldu: satıcı {}, ilan {}", seller.getId(), product.getId());
        return mapper.toSellerListingView(product, List.of());
    }

    /** Bilgileri günceller; durum ve {@code slug} değişmez. Kaldırılmış ilan da düzenlenebilir (ADR-038 §3). */
    @Transactional
    public SellerListingView update(long accountId, long productId, ListingRequest request) {
        var product = ownProduct(accountId, productId);
        product.update(request.name().trim(), description(request), ProductUnit.valueOf(request.unit()),
                Money.of(request.priceAmount(), CURRENCY), request.stockQuantity(),
                request.dispatchDays() == null ? product.getDispatchDays() : request.dispatchDays(), clock.instant());
        return view(product);
    }

    /** Satıcının ilanları; sayfadaki bütün fotoğraflar tek sorguda gelir. */
    @Transactional(readOnly = true)
    public PageResponse<SellerListingView> listOwn(long accountId, int page, int size) {
        var seller = sellers.requireApprovedSeller(accountId);
        var result = products.findOwnNewestFirst(seller.getId(), PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));
        var ids = result.getContent().stream().map(Product::getId).toList();
        var photosByProduct = ids.isEmpty()
                ? Map.<Long, List<ProductPhoto>>of()
                : photos.findAllOf(ids).stream().collect(Collectors.groupingBy(ProductPhoto::getProductId));
        return PageResponse.from(result.map(product ->
                mapper.toSellerListingView(product, photosByProduct.getOrDefault(product.getId(), List.of()))));
    }

    @Transactional(readOnly = true)
    public SellerListingView getOwn(long accountId, long productId) {
        return view(ownProduct(accountId, productId));
    }

    /**
     * Fotoğraf ekler (D3, D4): sona eklenir. 5 sınırı, tür, boyut ve piksel
     * sınırı denetlenir; iki boyut yazılır, transaction geri alınırsa silinir.
     *
     * @throws ApiException {@code PRODUCT_PHOTO_LIMIT_REACHED} veya alan bazlı {@code VALIDATION_FAILED}
     */
    @Transactional
    public SellerListingView addPhoto(long accountId, long productId, MultipartFile file) {
        var product = ownProduct(accountId, productId);
        int count = photos.countByProductId(productId);
        if (count >= MAX_PHOTOS) {
            throw new ApiException(ApiErrorCode.PRODUCT_PHOTO_LIMIT_REACHED, "error.productPhotoLimitReached");
        }
        var processed = photoProcessor.process(bytesOf(file));
        var cardKey = storage.write(processed.card());
        deleteOnRollback(cardKey);
        var largeKey = storage.write(processed.large());
        deleteOnRollback(largeKey);
        var now = clock.instant();
        photos.save(ProductPhoto.of(productId, count, cardKey, largeKey, processed.width(), processed.height(), now));
        product.touch(now);
        log.info("İlana fotoğraf eklendi: ilan {}, sıra {}", productId, count);
        return view(product);
    }

    /**
     * Fotoğrafı siler; kalanlar sıkıştırılır (0..n-1). Yayındaki veya
     * kaldırılmış ilanın son fotoğrafı silinemez: vitrine çıkabilen ilan
     * fotoğrafsız kalmaz.
     *
     * @throws ApiException {@code PRODUCT_NOT_FOUND} veya {@code PRODUCT_PHOTO_REQUIRED}
     */
    @Transactional
    public SellerListingView deletePhoto(long accountId, long productId, long photoId) {
        var product = ownProduct(accountId, productId);
        var photo = photos.findByIdAndProductId(photoId, productId).orElseThrow(ListingService::productNotFound);
        var all = photos.findByProductIdOrderByPositionAsc(productId);
        boolean visibleStatus = product.getStatus() == ProductStatus.PUBLISHED
                || product.getStatus() == ProductStatus.REMOVED;
        if (visibleStatus && all.size() == 1) {
            throw new ApiException(ApiErrorCode.PRODUCT_PHOTO_REQUIRED, "error.productPhotoLastRequired");
        }
        photos.delete(photo);
        var now = clock.instant();
        int position = 0;
        for (var remaining : all) {
            if (!remaining.getId().equals(photo.getId())) {
                remaining.moveTo(position++, now);
            }
        }
        product.touch(now);
        deleteAfterCommit(photo.getCardKey());
        deleteAfterCommit(photo.getLargeKey());
        return view(product);
    }

    /**
     * Yeni sıra: liste ilanın fotoğraflarını eksiksiz ve tekrarsız içermelidir;
     * ilk eleman kapak olur (D3). Pozisyon kısıtı transaction sonunda denetlenir.
     */
    @Transactional
    public SellerListingView reorderPhotos(long accountId, long productId, List<Long> photoIds) {
        var product = ownProduct(accountId, productId);
        var all = photos.findByProductIdOrderByPositionAsc(productId);
        var byId = all.stream().collect(Collectors.toMap(ProductPhoto::getId, Function.identity()));
        if (photoIds.size() != all.size() || new HashSet<>(photoIds).size() != photoIds.size()
                || !byId.keySet().containsAll(photoIds)) {
            throw ApiException.invalidField("photoIds", "validation.photoOrder.invalid");
        }
        var now = clock.instant();
        for (int i = 0; i < photoIds.size(); i++) {
            byId.get(photoIds.get(i)).moveTo(i, now);
        }
        product.touch(now);
        return view(product);
    }

    /**
     * Satıcının yayın geçişi (D1): yayına al, yayından çek, arşivle, geri al.
     * Vitrine çıkaran geçiş en az bir fotoğraf ister.
     *
     * @throws ApiException {@code PRODUCT_INVALID_TRANSITION} veya {@code PRODUCT_PHOTO_REQUIRED}
     */
    @Transactional
    public SellerListingView transition(long accountId, long productId, ProductTransition transition) {
        if (transition.actor() != ProductTransition.Actor.SELLER) {
            throw new IllegalArgumentException("Satıcı geçişi değil: " + transition);
        }
        var product = ownProduct(accountId, productId);
        apply(product, transition, null, accountId);
        return view(product);
    }

    /**
     * Geçişin ortak adımları (satıcı ve admin): izin, fotoğraf koşulu, durum ve
     * geçmiş kaydı. Admin kaldırma servisi de bunu kullanır.
     */
    void apply(Product product, ProductTransition transition, String reason, long actorAccountId) {
        if (!product.allows(transition)) {
            throw new ApiException(ApiErrorCode.PRODUCT_INVALID_TRANSITION, "error.productInvalidTransition");
        }
        if (transition.requiresPhoto() && photos.countByProductId(product.getId()) == 0) {
            throw new ApiException(ApiErrorCode.PRODUCT_PHOTO_REQUIRED, "error.productPhotoRequired");
        }
        var now = clock.instant();
        var previous = product.getStatus();
        product.apply(transition, reason, now);
        statusChanges.save(ProductStatusChange.record(product.getId(), previous, product.getStatus(), reason,
                actorAccountId, now));
        log.info("İlan geçişi: ilan {}, {} -> {}", product.getId(), previous, product.getStatus());
    }

    /** Onaylı satıcı kapısı + sorgu seviyesinde sahiplik (D2). */
    Product ownProduct(long accountId, long productId) {
        var seller = sellers.requireApprovedSeller(accountId);
        return products.findByIdAndSellerId(productId, seller.getId()).orElseThrow(ListingService::productNotFound);
    }

    private SellerListingView view(Product product) {
        return mapper.toSellerListingView(product, photos.findByProductIdOrderByPositionAsc(product.getId()));
    }

    private String uniqueSlug(String name) {
        var base = Slugs.slugify(name, SLUG_FALLBACK);
        var candidate = base;
        for (int suffix = 2; products.existsBySlug(candidate); suffix++) {
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private static String description(ListingRequest request) {
        return request.description() == null || request.description().isBlank() ? null : request.description().trim();
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException cause) {
            throw new UncheckedIOException("Yüklenen fotoğraf okunamadı", cause);
        }
    }

    private void deleteOnRollback(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);
                }
            }
        });
    }

    private void deleteAfterCommit(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(key);
            }
        });
    }

    static ApiException productNotFound() {
        return new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND, "error.productNotFound");
    }
}

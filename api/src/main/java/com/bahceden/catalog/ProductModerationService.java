package com.bahceden.catalog;

import com.bahceden.account.AccountRepository;
import com.bahceden.catalog.domain.Product;
import com.bahceden.catalog.domain.ProductPhoto;
import com.bahceden.catalog.domain.ProductStatus;
import com.bahceden.catalog.domain.ProductTransition;
import com.bahceden.catalog.dto.AdminProductSummary;
import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.i18n.Messages;
import com.bahceden.common.mail.MailSender;
import com.bahceden.common.web.PageResponse;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin'in ilan denetimi (ADR-038 §3, add-product-listings D7): listeleme,
 * gerekçeyle kaldırma ve yeniden açma.
 *
 * <p>Geçişin kendisi (izin, fotoğraf koşulu, geçmiş kaydı) satıcı geçişleriyle
 * aynı yerden, {@link ListingService#apply} üzerinden yapılır; kural iki kez
 * yazılmaz (K9). Karar satıcıya hesabının dilinde e-postayla bildirilir;
 * gönderim hatası kararı geri almaz (ADR-019). Seed satıcılarının hesabı yoktur,
 * onlarda e-posta adımı atlanır.
 */
@Service
public class ProductModerationService {

    private static final Logger log = LoggerFactory.getLogger(ProductModerationService.class);

    private final ProductRepository products;
    private final ProductPhotoRepository photos;
    private final ListingService listings;
    private final ProductMapper mapper;
    private final AccountRepository accounts;
    private final MailSender mailSender;
    private final Messages messages;
    private final BahcedenProperties properties;

    ProductModerationService(ProductRepository products,
                             ProductPhotoRepository photos,
                             ListingService listings,
                             ProductMapper mapper,
                             AccountRepository accounts,
                             MailSender mailSender,
                             Messages messages,
                             BahcedenProperties properties) {
        this.products = products;
        this.photos = photos;
        this.listings = listings;
        this.mapper = mapper;
        this.accounts = accounts;
        this.mailSender = mailSender;
        this.messages = messages;
        this.properties = properties;
    }

    /** Durumla süzülmüş ilanlar; üretici ve kapaklar sabit sayıda sorguyla gelir. */
    @Transactional(readOnly = true)
    public PageResponse<AdminProductSummary> list(ProductStatus status, int page, int size) {
        var result = products.findForModeration(status, PageRequest.of(page, Math.min(size, ListingService.MAX_PAGE_SIZE)));
        var ids = result.getContent().stream().map(Product::getId).toList();
        Map<Long, ProductPhoto> covers = ids.isEmpty()
                ? Map.of()
                : photos.findCovers(ids).stream().collect(Collectors.toMap(ProductPhoto::getProductId, Function.identity()));
        return PageResponse.from(result.map(product ->
                mapper.toAdminSummary(product, Optional.ofNullable(covers.get(product.getId())))));
    }

    /** Gerekçeyle kaldırır: ilan vitrinden ve detaydan düşer, satıcı gerekçeyi görür. */
    @Transactional
    public AdminProductSummary remove(long adminAccountId, long productId, String reason) {
        return decide(adminAccountId, productId, ProductTransition.REMOVE, reason.trim());
    }

    /** Yeniden açar: ilan yayına döner, gerekçe temizlenir. */
    @Transactional
    public AdminProductSummary reinstate(long adminAccountId, long productId) {
        return decide(adminAccountId, productId, ProductTransition.REINSTATE, null);
    }

    private AdminProductSummary decide(long adminAccountId, long productId, ProductTransition transition, String reason) {
        var product = products.findById(productId).orElseThrow(ListingService::productNotFound);
        listings.apply(product, transition, reason, adminAccountId);
        notifySeller(product, transition, reason);
        log.info("İlan denetimi: ilan {}, {}, admin {}", productId, transition, adminAccountId);
        var cover = photos.findByProductIdOrderByPositionAsc(productId).stream().findFirst();
        return mapper.toAdminSummary(product, cover);
    }

    private void notifySeller(Product product, ProductTransition transition, String reason) {
        product.getSeller().getAccountId().flatMap(accounts::findById).ifPresent(owner -> {
            var locale = owner.getLocale();
            var link = properties.webOrigin() + "/seller/products/" + product.getId();
            var prefix = transition == ProductTransition.REMOVE ? "mail.listingRemoved" : "mail.listingReinstated";
            var body = transition.requiresReason()
                    ? messages.get(prefix + ".body", locale, product.getName(), reason, link)
                    : messages.get(prefix + ".body", locale, product.getName(), link);
            if (!mailSender.send(owner.getEmail(), messages.get(prefix + ".subject", locale), body)) {
                log.warn("İlan denetimi e-postası gönderilemedi; karar geçerli: ilan {}", product.getId());
            }
        });
    }
}

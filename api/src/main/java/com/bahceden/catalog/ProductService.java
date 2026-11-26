package com.bahceden.catalog;

import com.bahceden.catalog.domain.Product;
import com.bahceden.catalog.domain.ProductPhoto;
import com.bahceden.catalog.dto.ProductDetail;
import com.bahceden.catalog.dto.ProductPhotoView;
import com.bahceden.catalog.dto.ProductSummary;
import com.bahceden.common.cache.StorefrontCache;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.common.web.PageResponse;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vitrin katalog iş kuralları.
 *
 * <p>İş mantığı controller'da değil burada yaşar (AGENTS.md §11.2): sayfa boyutu
 * sınırı bir kaynak koruma kuralıdır ve HTTP katmanından bağımsız olarak
 * geçerlidir.
 */
@Service
public class ProductService {

    /**
     * Sayfa boyutunun üst sınırı sunucuda sabittir (design.md D3): aksi halde
     * tek bir istekle veritabanı tüketilebilir. Sınır aşıldığında istek
     * reddedilmez, değer sınıra çekilir: bu bir istemci hatası değil kaynak
     * korumasıdır ve vitrinde kullanıcıya hata göstermek doğru olmaz.
     */
    public static final int MAX_PAGE_SIZE = 50;

    static final int DEFAULT_PAGE_SIZE = 20;

    /** Cache'lenen ilk sayfa sayısı (ADR-109): derin sayfalar seyrek okunur, cache'i şişirmez. */
    public static final int CACHED_PAGES = 3;

    private final ProductRepository repository;
    private final ProductPhotoRepository photos;
    private final ProductMapper mapper;

    ProductService(ProductRepository repository, ProductPhotoRepository photos, ProductMapper mapper) {
        this.repository = repository;
        this.photos = photos;
        this.mapper = mapper;
    }

    /**
     * Vitrin listesi. Kapaklar sayfadaki ürün kimlikleriyle <strong>tek</strong>
     * ek sorguda gelir (add-product-listings D6): fetch join ile sayfalama
     * bellekte yapılırdı.
     *
     * <p>İlk {@value #CACHED_PAGES} sayfa kısa süre cache'lenir (ADR-109); anahtar
     * sınıra çekilmiş boyutla kurulur, sınır üstü boyutlar ayrı kayıt açmaz.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = StorefrontCache.LIST,
            key = "#page + ':' + T(java.lang.Math).min(#size, T(com.bahceden.catalog.ProductService).MAX_PAGE_SIZE)",
            condition = "#page >= 0 && #page < T(com.bahceden.catalog.ProductService).CACHED_PAGES")
    public PageResponse<ProductSummary> listPublished(int page, int size) {
        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);
        var products = repository.findPublished(PageRequest.of(page, effectiveSize));
        var ids = products.getContent().stream().map(Product::getId).toList();
        Map<Long, ProductPhoto> covers = ids.isEmpty()
                ? Map.of()
                : photos.findCovers(ids).stream().collect(Collectors.toMap(ProductPhoto::getProductId, Function.identity()));
        return PageResponse.from(products.map(product ->
                mapper.toSummary(product, Optional.ofNullable(covers.get(product.getId())))));
    }

    /**
     * Verilen ürünlerin kapak fotoğrafları, tek sorguda (sepet ve sipariş
     * satırları için; add-cart-and-checkout D4). Fotoğrafı olmayan ürün haritada yoktur.
     */
    @Transactional(readOnly = true)
    public Map<Long, ProductPhotoView> coverViews(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return photos.findCovers(productIds).stream()
                .collect(Collectors.toMap(ProductPhoto::getProductId, mapper::toPhotoView));
    }

    /**
     * Ürün detayı; vitrinde görünmeyen ürün yokmuş gibi 404 döner.
     *
     * <p>Kısa süre cache'lenir (ADR-109). 404 istisnayla döndüğü için cache'e
     * yazılmaz: görünmeyen ürün cache'ten hiçbir zaman dönmez.
     *
     * @throws ApiException {@code PRODUCT_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = StorefrontCache.PRODUCT, key = "#slug")
    public ProductDetail detail(String slug) {
        var product = repository.findVisibleBySlug(slug)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND, "error.productNotFound"));
        return mapper.toDetail(product, photos.findByProductIdOrderByPositionAsc(product.getId()));
    }
}

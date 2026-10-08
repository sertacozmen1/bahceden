package com.bahceden.catalog;

import com.bahceden.catalog.domain.Product;
import com.bahceden.catalog.domain.ProductPhoto;
import com.bahceden.catalog.dto.ProductDetail;
import com.bahceden.catalog.dto.ProductSummary;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.common.web.PageResponse;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    static final int MAX_PAGE_SIZE = 50;

    static final int DEFAULT_PAGE_SIZE = 20;

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
     */
    @Transactional(readOnly = true)
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
     * Ürün detayı; vitrinde görünmeyen ürün yokmuş gibi 404 döner.
     *
     * @throws ApiException {@code PRODUCT_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public ProductDetail detail(String slug) {
        var product = repository.findVisibleBySlug(slug)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PRODUCT_NOT_FOUND, "error.productNotFound"));
        return mapper.toDetail(product, photos.findByProductIdOrderByPositionAsc(product.getId()));
    }
}

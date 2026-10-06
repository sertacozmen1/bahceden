package com.bahceden.catalog;

import com.bahceden.catalog.dto.ProductSummary;
import com.bahceden.common.web.PageResponse;
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
    private final ProductMapper mapper;

    ProductService(ProductRepository repository, ProductMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummary> listPublished(int page, int size) {
        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);
        var products = repository.findPublished(PageRequest.of(page, effectiveSize));
        return PageResponse.from(products.map(mapper::toSummary));
    }
}

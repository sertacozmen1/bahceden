package com.bahceden.catalog;

import com.bahceden.catalog.domain.Product;
import com.bahceden.seller.domain.Seller;
import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.catalog.dto.ProductSummary;
import com.bahceden.catalog.dto.SellerSummary;
import org.springframework.stereotype.Component;

/**
 * Entity'den DTO'ya dönüşüm.
 *
 * <p>Elle yazılır: mapper kütüphanesi ADR-002 bütçesinde yok ve bu ölçekte elle
 * yazılmış dönüşüm daha az sihir, daha anlaşılır hata veriyor.
 *
 * <p>Entity asla dışarıya verilmez (AGENTS.md §11.2): API'ye yalnızca DTO döner,
 * böylece veritabanı şeması ile sözleşme birbirine kilitlenmez.
 */
@Component
public class ProductMapper {

    public ProductSummary toSummary(Product product) {
        return new ProductSummary(
                product.getSlug(),
                product.getName(),
                product.getUnit(),
                MoneyView.from(product.getPrice()),
                product.getStockQuantity(),
                toSellerSummary(product.getSeller()));
    }

    private SellerSummary toSellerSummary(Seller seller) {
        return new SellerSummary(seller.getSlug(), seller.getDisplayName(), seller.getCity());
    }
}

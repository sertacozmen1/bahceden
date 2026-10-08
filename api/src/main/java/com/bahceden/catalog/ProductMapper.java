package com.bahceden.catalog;

import com.bahceden.catalog.domain.Product;
import com.bahceden.catalog.domain.ProductPhoto;
import com.bahceden.seller.domain.Seller;
import com.bahceden.catalog.dto.AdminProductSummary;
import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.catalog.dto.ProductDetail;
import com.bahceden.catalog.dto.ProductPhotoView;
import com.bahceden.catalog.dto.ProductSummary;
import com.bahceden.catalog.dto.SellerListingView;
import com.bahceden.catalog.dto.SellerSummary;
import java.util.List;
import java.util.Optional;
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

    public ProductSummary toSummary(Product product, Optional<ProductPhoto> cover) {
        return new ProductSummary(
                product.getSlug(),
                product.getName(),
                product.getUnit(),
                MoneyView.from(product.getPrice()),
                product.getStockQuantity(),
                toSellerSummary(product.getSeller()),
                cover.map(this::toPhotoView).orElse(null));
    }

    public AdminProductSummary toAdminSummary(Product product, Optional<ProductPhoto> cover) {
        return new AdminProductSummary(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getStatus(),
                product.getStatusReason().orElse(null),
                toSellerSummary(product.getSeller()),
                cover.map(this::toPhotoView).orElse(null),
                product.getUpdatedAt());
    }

    public ProductDetail toDetail(Product product, List<ProductPhoto> photos) {
        return new ProductDetail(
                product.getSlug(),
                product.getName(),
                product.getDescription(),
                product.getUnit(),
                MoneyView.from(product.getPrice()),
                product.getStockQuantity(),
                toSellerSummary(product.getSeller()),
                photos.stream().map(this::toPhotoView).toList());
    }

    public ProductPhotoView toPhotoView(ProductPhoto photo) {
        return new ProductPhotoView(photo.getId(), photo.getCardKey(), photo.getLargeKey(), photo.getWidth(),
                photo.getHeight());
    }

    public SellerListingView toSellerListingView(Product product, List<ProductPhoto> photos) {
        return new SellerListingView(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getDescription(),
                product.getUnit(),
                MoneyView.from(product.getPrice()),
                product.getStockQuantity(),
                product.getStatus(),
                product.getStatusReason().orElse(null),
                product.getPublishedAt(),
                product.getUpdatedAt(),
                photos.stream().map(this::toPhotoView).toList());
    }

    private SellerSummary toSellerSummary(Seller seller) {
        return new SellerSummary(seller.getSlug(), seller.getDisplayName(), seller.getCity());
    }
}

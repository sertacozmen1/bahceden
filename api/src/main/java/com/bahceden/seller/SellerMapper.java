package com.bahceden.seller;

import com.bahceden.seller.domain.Seller;
import com.bahceden.seller.domain.SellerDocument;
import com.bahceden.seller.dto.AdminSellerDetail;
import com.bahceden.seller.dto.AdminSellerSummary;
import com.bahceden.seller.dto.SellerSelfView;
import com.bahceden.seller.dto.SellerStatusChangeView;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Satıcı entity'lerinden DTO'lara elle yazılmış dönüşüm (AGENTS.md §11.2):
 * entity API'ye sızmaz.
 */
@Component
public class SellerMapper {

    public SellerSelfView toSelfView(Seller seller, SellerDocument document) {
        return new SellerSelfView(
                seller.getSlug(),
                seller.getDisplayName(),
                seller.getCity(),
                seller.getPhone().orElse(null),
                seller.getStatus(),
                seller.getStatusReason().orElse(null),
                document.getDocumentType(),
                seller.getStatusChangedAt());
    }

    public AdminSellerSummary toAdminSummary(Seller seller) {
        return new AdminSellerSummary(seller.getId(), seller.getDisplayName(), seller.getCity(),
                seller.getStatus(), seller.getCreatedAt(), seller.getStatusChangedAt());
    }

    public AdminSellerDetail toAdminDetail(Seller seller, Optional<SellerDocument> document,
                                           Optional<String> accountEmail, List<SellerStatusChangeView> history) {
        return new AdminSellerDetail(
                seller.getId(),
                seller.getSlug(),
                seller.getDisplayName(),
                seller.getCity(),
                seller.getPhone().orElse(null),
                accountEmail.orElse(null),
                seller.getStatus(),
                seller.getStatusReason().orElse(null),
                document.map(SellerDocument::getDocumentType).orElse(null),
                seller.getCreatedAt(),
                seller.getStatusChangedAt(),
                List.copyOf(history));
    }
}

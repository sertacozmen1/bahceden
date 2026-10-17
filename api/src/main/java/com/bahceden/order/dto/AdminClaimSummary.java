package com.bahceden.order.dto;

import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.order.domain.DefectClaimStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Admin talep listesinin satırı (ADR-084, ADR-086). Alıcının kişisel verisini
 * taşımaz; ayrıntı ve fotoğraflar detaydadır.
 *
 * @param id            talebin kimliği
 * @param orderNumber   sipariş numarası
 * @param sellerOrderId satıcı siparişinin kimliği
 * @param sellerName    üreticinin görünen adı
 * @param status        talebin durumu
 * @param createdAt     açıldığı an (UTC)
 * @param refundAmount  iade edildiyse toplam; yoksa {@code null}
 */
@Schema(name = "AdminClaimSummary", description = "Admin talep listesinin satırı")
public record AdminClaimSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Talebin kimliği", example = "5")
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sipariş numarası", example = "BHC-7K2M9QXA4T")
        String orderNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı siparişinin kimliği", example = "17")
        long sellerOrderId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Üreticinin görünen adı",
                example = "Üretici Bahçesi")
        String sellerName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Talebin durumu", example = "ESCALATED")
        DefectClaimStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Açıldığı an (UTC)",
                example = "2026-10-13T10:00:00Z")
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "İade edildiyse alıcıya dönen toplam")
        MoneyView refundAmount) {
}

package com.bahceden.order.dto;

import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.order.domain.SellerOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Satıcının gelen siparişler listesindeki satır (orders spec'i).
 *
 * @param id              satıcı siparişinin kimliği
 * @param orderNumber     sipariş numarası
 * @param status          durum
 * @param itemsTotal      ürün tutarı
 * @param sellerPayout    satıcının hakedişi
 * @param dispatchDueAt   kargoya verme son tarihi
 * @param dispatchOverdue son tarih geçti mi
 * @param createdAt       oluşma anı (UTC)
 */
@Schema(name = "SellerOrderSummary", description = "Satıcının gelen siparişler listesindeki satır")
public record SellerOrderSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı siparişinin kimliği", example = "17")
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sipariş numarası", example = "BHC-7K2M9QXA4T")
        String orderNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Durum", example = "NEW")
        SellerOrderStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün tutarı")
        MoneyView itemsTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcının hakedişi: ürün tutarı − komisyon")
        MoneyView sellerPayout,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Kargoya verme son tarihi (UTC)", example = "2026-10-13T20:59:59Z")
        Instant dispatchDueAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Son tarih geçti ve hâlâ kargoya verilmedi mi",
                example = "false")
        boolean dispatchOverdue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Oluşma anı (UTC)",
                example = "2026-10-08T09:30:00Z")
        Instant createdAt) {
}

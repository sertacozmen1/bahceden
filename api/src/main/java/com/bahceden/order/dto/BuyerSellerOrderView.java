package com.bahceden.order.dto;

import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.catalog.dto.SellerSummary;
import com.bahceden.order.domain.CancelledBy;
import com.bahceden.order.domain.SellerOrderStatus;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Alıcının gördüğü satıcı siparişi (orders spec'i). Komisyon ve hakediş
 * alıcıya gösterilmez.
 *
 * @param id              satıcı siparişinin kimliği (iptal bu kimlikle)
 * @param seller          üretici
 * @param status          durum
 * @param items           satırlar
 * @param itemsTotal      ürün tutarı
 * @param shippingFee     kargo ücreti
 * @param dispatchDays    kargoya verme süresi, iş günü
 * @param dispatchDueAt   kargoya verme son tarihi; ödenmemişse {@code null}
 * @param dispatchOverdue son tarih geçti ve hâlâ kargoya verilmedi mi
 * @param cancelledBy     iptal eden taraf; iptal edilmediyse {@code null}
 * @param cancelReason    iptal gerekçesi; yoksa {@code null}
 * @param cancellable     alıcı şu an iptal edebilir mi
 */
@Schema(name = "BuyerSellerOrderView", description = "Alıcının gördüğü satıcı siparişi")
public record BuyerSellerOrderView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı siparişinin kimliği", example = "17")
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Üretici")
        SellerSummary seller,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı siparişinin durumu", example = "NEW")
        SellerOrderStatus status,
        @ArraySchema(schema = @Schema(implementation = OrderItemView.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satırlar"))
        List<OrderItemView> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün tutarı")
        MoneyView itemsTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kargo ücreti")
        MoneyView shippingFee,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kargoya verme süresi, iş günü", example = "2")
        int dispatchDays,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Kargoya verme son tarihi (UTC); ödenmemişse boş", example = "2026-10-13T20:59:59Z")
        Instant dispatchDueAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Son tarih geçti ve sipariş hâlâ kargoya verilmedi mi", example = "false")
        boolean dispatchOverdue,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "İptal eden taraf",
                example = "BUYER")
        CancelledBy cancelledBy,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "İptal gerekçesi",
                example = "Stok tükendi.")
        String cancelReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alıcı şu an iptal edebilir mi",
                example = "true")
        boolean cancellable) {
}

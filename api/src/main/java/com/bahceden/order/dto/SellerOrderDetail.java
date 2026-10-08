package com.bahceden.order.dto;

import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.order.domain.CancelledBy;
import com.bahceden.order.domain.SellerOrderStatus;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Satıcının gördüğü satıcı siparişi (orders spec'i "Satıcı kendi satıcı
 * siparişlerini görür"). Alıcının adresinden yalnızca il ve ilçe bulunur;
 * adı, telefonu ve açık adresi bulunmaz (K10).
 *
 * @param id               satıcı siparişinin kimliği
 * @param orderNumber      sipariş numarası
 * @param status           durum
 * @param createdAt        oluşma anı (UTC)
 * @param items            satırlar
 * @param itemsTotal       ürün tutarı
 * @param commissionAmount komisyon
 * @param sellerPayout     hakediş
 * @param dispatchDays     kargoya verme süresi, iş günü
 * @param dispatchDueAt    kargoya verme son tarihi
 * @param dispatchOverdue  son tarih geçti mi
 * @param provinceName     alıcının ili
 * @param district         alıcının ilçesi
 * @param cancelledBy      iptal eden taraf; yoksa {@code null}
 * @param cancelReason     iptal gerekçesi; yoksa {@code null}
 * @param cancellable      satıcı şu an iptal edebilir mi
 */
@Schema(name = "SellerOrderDetail", description = "Satıcının gördüğü satıcı siparişi")
public record SellerOrderDetail(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı siparişinin kimliği", example = "17")
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sipariş numarası", example = "BHC-7K2M9QXA4T")
        String orderNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Durum", example = "NEW")
        SellerOrderStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Oluşma anı (UTC)",
                example = "2026-10-08T09:30:00Z")
        Instant createdAt,
        @ArraySchema(schema = @Schema(implementation = OrderItemView.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satırlar"))
        List<OrderItemView> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün tutarı")
        MoneyView itemsTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Komisyon (ADR-055)")
        MoneyView commissionAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Hakediş: ürün tutarı − komisyon")
        MoneyView sellerPayout,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kargoya verme süresi, iş günü", example = "2")
        int dispatchDays,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Kargoya verme son tarihi (UTC)", example = "2026-10-13T20:59:59Z")
        Instant dispatchDueAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Son tarih geçti ve hâlâ kargoya verilmedi mi",
                example = "false")
        boolean dispatchOverdue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alıcının ili", example = "İzmir")
        String provinceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alıcının ilçesi", example = "Bornova")
        String district,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "İptal eden taraf",
                example = "BUYER")
        CancelledBy cancelledBy,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "İptal gerekçesi",
                example = "Stok tükendi.")
        String cancelReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı şu an iptal edebilir mi",
                example = "true")
        boolean cancellable) {
}

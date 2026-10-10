package com.bahceden.order.dto;

import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.order.domain.SellerOrderStatus;
import com.bahceden.shipping.dto.ShipmentView;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Admin'in teslim edilemeyen satıcı siparişi detayı (add-shipments D7).
 * Admin alıcıyla iletişim için adını ve telefonunu görür; açık adres
 * gösterilmez (K10).
 *
 * @param id               satıcı siparişinin kimliği
 * @param orderNumber      sipariş numarası
 * @param status           durum
 * @param sellerName       üreticinin görünen adı
 * @param createdAt        oluşma anı (UTC)
 * @param items            satırlar
 * @param itemsTotal       ürün tutarı
 * @param shippingFee      kargo ücreti
 * @param refundableAmount iade kararında alıcıya dönecek tutar
 * @param recipientName    alıcı adı
 * @param recipientPhone   alıcı telefonu
 * @param provinceName     teslimat ili
 * @param district         teslimat ilçesi
 * @param shipment         gönderi
 * @param resolvable       admin şu an karar verebilir mi
 */
@Schema(name = "AdminSellerOrderDetail", description = "Admin'in gördüğü teslim edilemeyen satıcı siparişi")
public record AdminSellerOrderDetail(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı siparişinin kimliği", example = "17")
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sipariş numarası", example = "BHC-7K2M9QXA4T")
        String orderNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Durum", example = "DELIVERY_FAILED")
        SellerOrderStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Üreticinin görünen adı",
                example = "Yılmaz Bahçesi")
        String sellerName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Oluşma anı (UTC)",
                example = "2026-10-08T09:30:00Z")
        Instant createdAt,
        @ArraySchema(schema = @Schema(implementation = OrderItemView.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satırlar"))
        List<OrderItemView> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün tutarı")
        MoneyView itemsTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kargo ücreti")
        MoneyView shippingFee,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "İade kararında alıcıya dönecek tutar: ürünler + kargo ücreti")
        MoneyView refundableAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alıcı adı (iletişim için)",
                example = "Ayşe Örnek")
        String recipientName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alıcı telefonu (iletişim için)",
                example = "+905550000000")
        String recipientPhone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Teslimat ili", example = "İzmir")
        String provinceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Teslimat ilçesi", example = "Bornova")
        String district,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "Kargo gönderisi")
        ShipmentView shipment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Admin şu an karar verebilir mi",
                example = "true")
        boolean resolvable) {
}

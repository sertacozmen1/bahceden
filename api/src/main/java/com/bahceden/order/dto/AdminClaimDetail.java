package com.bahceden.order.dto;

import com.bahceden.catalog.dto.MoneyView;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Admin'in talep detayı (ADR-080, ADR-084): talebin kendisi (fotoğraflar,
 * iki tarafın beyanı), satıcı siparişinin satırları ve satın alınan adetler
 * (admin satın alınana kadar iade edebilir), tutarlar. Alıcının adı, telefonu
 * ve adresi yoktur.
 *
 * @param claim         talep
 * @param orderNumber   sipariş numarası
 * @param sellerOrderId satıcı siparişinin kimliği
 * @param sellerName    üreticinin görünen adı
 * @param items         satıcı siparişinin satırları; satın alınan adet satırdadır
 * @param itemsTotal    ürün tutarı
 * @param shippingFee   kargo ücreti
 * @param decidable     admin şu an karar verebilir mi (talep yönetimde mi)
 */
@Schema(name = "AdminClaimDetail", description = "Admin'in ayıplı ürün talebi detayı")
public record AdminClaimDetail(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Talep")
        DefectClaimView claim,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sipariş numarası", example = "BHC-7K2M9QXA4T")
        String orderNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı siparişinin kimliği", example = "17")
        long sellerOrderId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Üreticinin görünen adı",
                example = "Üretici Bahçesi")
        String sellerName,
        @ArraySchema(schema = @Schema(implementation = OrderItemView.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Satıcı siparişinin satırları"))
        List<OrderItemView> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün tutarı")
        MoneyView itemsTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kargo ücreti")
        MoneyView shippingFee,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Admin şu an karar verebilir mi",
                example = "true")
        boolean decidable) {

    public AdminClaimDetail {
        items = List.copyOf(items);
    }
}

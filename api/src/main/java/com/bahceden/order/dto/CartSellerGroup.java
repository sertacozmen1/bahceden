package com.bahceden.order.dto;

import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.catalog.dto.SellerSummary;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Sepetin bir satıcıya düşen kısmı (cart-and-checkout spec'i): her satıcı ayrı
 * paket gönderir ve ayrı kargo ücreti doğurur (ADR-058).
 *
 * @param seller                üretici
 * @param lines                 satırlar
 * @param itemsTotal            satın alınabilir satırların toplamı
 * @param shippingFee           bu satıcının kargo ücreti
 * @param freeShippingRemaining ücretsiz kargoya kalan tutar; sıfırsa kargo ücretsiz
 * @param dispatchDays          satın alınabilir ürünlerin en uzun kargoya verme süresi, iş günü
 */
@Schema(name = "CartSellerGroup", description = "Sepetin bir satıcıya düşen kısmı")
public record CartSellerGroup(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Üretici")
        SellerSummary seller,
        @ArraySchema(schema = @Schema(implementation = CartLine.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satırlar"))
        List<CartLine> lines,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satın alınabilir satırların toplamı")
        MoneyView itemsTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Bu satıcının kargo ücreti")
        MoneyView shippingFee,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ücretsiz kargoya kalan tutar; sıfırsa ücretsiz")
        MoneyView freeShippingRemaining,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Satın alınabilir ürünlerin en uzun kargoya verme süresi, iş günü; satır yoksa 0", example = "2")
        int dispatchDays) {
}

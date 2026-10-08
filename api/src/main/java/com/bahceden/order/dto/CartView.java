package com.bahceden.order.dto;

import com.bahceden.catalog.dto.MoneyView;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Hesabın sepeti (cart-and-checkout spec'i "Sepet hesaba bağlıdır ve
 * satıcılara göre gruplanır"). Tutarların tamamını sunucu hesaplar; istemci
 * yalnızca gösterir (§12.1).
 *
 * @param groups              satıcı grupları
 * @param itemCount           satın alınabilir satırların toplam adedi (başlıktaki sayı)
 * @param itemsTotal          ürün toplamı
 * @param shippingTotal       kargo toplamı
 * @param grandTotal          genel toplam
 * @param hasUnavailableItems satın alınamayan satır var mı; varsa sipariş verilemez
 */
@Schema(name = "CartView", description = "Hesabın sepeti, satıcılara göre gruplu")
public record CartView(
        @ArraySchema(schema = @Schema(implementation = CartSellerGroup.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı grupları"))
        List<CartSellerGroup> groups,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satın alınabilir satırların toplam adedi",
                example = "3")
        int itemCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün toplamı")
        MoneyView itemsTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kargo toplamı")
        MoneyView shippingTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Genel toplam: ürünler + kargo")
        MoneyView grandTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Satın alınamayan satır var mı; varsa sipariş verilemez", example = "false")
        boolean hasUnavailableItems) {
}

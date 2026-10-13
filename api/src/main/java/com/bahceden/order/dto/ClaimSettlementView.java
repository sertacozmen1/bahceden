package com.bahceden.order.dto;

import com.bahceden.catalog.dto.MoneyView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Bir yanıtın para etkisi (ADR-082, ADR-086): satıcı kararını vermeden önce
 * görür. Tutarları sunucu hesaplar; ekran yalnızca gösterir.
 *
 * @param refundAmount     alıcıya iade edilecek toplam (ürün ve, tam iadede, kargo)
 * @param shippingRefunded kargo ücreti de iade ediliyor mu (yalnızca tam iadede)
 * @param commissionAfter  iadeden sonraki komisyon
 * @param payoutAfter      iadeden sonraki satıcı hakedişi
 */
@Schema(name = "ClaimSettlementView", description = "Ayıplı ürün iadesinin para etkisi")
public record ClaimSettlementView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alıcıya iade edilecek toplam")
        MoneyView refundAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kargo ücreti de iade ediliyor mu",
                example = "false")
        boolean shippingRefunded,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İadeden sonraki komisyon")
        MoneyView commissionAfter,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İadeden sonraki satıcı hakedişi")
        MoneyView payoutAfter) {
}

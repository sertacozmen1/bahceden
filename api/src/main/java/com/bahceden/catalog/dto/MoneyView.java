package com.bahceden.catalog.dto;

import com.bahceden.common.money.Money;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Para değerinin sözleşmedeki temsili (ADR-006).
 *
 * <p>Tutar her zaman minor unit (kuruş) tamsayıdır ve yanında açık para birimi
 * taşır. İstemci bu değer üzerinde <strong>hesap yapmaz</strong>, yalnızca
 * biçimlendirir: tüm toplamlar sunucuda hesaplanır.
 *
 * @param amount   tutar, kuruş cinsinden (12550 = 125,50 TL)
 * @param currency ISO 4217 para birimi kodu
 */
@Schema(name = "Money", description = "Para değeri: kuruş cinsinden tamsayı tutar ve para birimi")
public record MoneyView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Tutar, kuruş cinsinden", example = "12550") long amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ISO 4217 para birimi kodu", example = "TRY") String currency) {

    public static MoneyView from(Money money) {
        return new MoneyView(money.amountInMinorUnits(), money.currencyCode());
    }
}

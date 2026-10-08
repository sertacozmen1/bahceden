package com.bahceden.common.money;

import com.bahceden.common.i18n.SupportedLocale;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;

/**
 * E-postalardaki para gösterimi (ADR-006): kuruş tutarı hesabın dilinde
 * biçimlenir. Ekrandaki gösterim {@code web}'in işidir; bu yardımcı yalnızca
 * sunucunun ürettiği metinler (e-posta) içindir ve hesap yapmaz.
 */
public final class MoneyFormat {

    private MoneyFormat() {
    }

    /** Örnek: 12550, TRY, tr → {@code ₺125,50}. */
    public static String format(long amountInMinorUnits, String currencyCode, SupportedLocale locale) {
        var currency = Currency.getInstance(currencyCode);
        var format = NumberFormat.getCurrencyInstance(locale.locale());
        format.setCurrency(currency);
        return format.format(BigDecimal.valueOf(amountInMinorUnits, currency.getDefaultFractionDigits()));
    }
}

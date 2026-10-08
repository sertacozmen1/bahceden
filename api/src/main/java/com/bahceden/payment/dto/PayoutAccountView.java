package com.bahceden.payment.dto;

import com.bahceden.payment.domain.PayoutAccountStatus;
import com.bahceden.payment.domain.PayoutLegalType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Satıcının kendi ödeme hesabı, maskeli (seller-payout-details spec'i, D2).
 *
 * <p>IBAN ve kimlik numarası hiçbir yanıtta tam dönmez (K10): yalnızca son
 * dört hane görünür. Satıcı değiştirmek için değeri yeniden tam girer.
 *
 * @param legalType  satıcı türü
 * @param legalName  yasal ad
 * @param taxIdMasked kimlik numarası, son dört hane dışında yıldızlı
 * @param taxOffice  vergi dairesi; yoksa {@code null}
 * @param ibanMasked IBAN, ülke kodu ve son dört hane dışında yıldızlı
 * @param status     hesabın durumu
 * @param updatedAt  son güncelleme anı (UTC)
 */
@Schema(name = "PayoutAccountView", description = "Satıcının ödeme hesabı; numaralar maskeli")
public record PayoutAccountView(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı türü", example = "INDIVIDUAL")
        PayoutLegalType legalType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Yasal ad", example = "Örnek Üretici")
        String legalName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Kimlik numarası; yalnızca son dört hane görünür", example = "*******0078")
        String taxIdMasked,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "Vergi dairesi",
                example = "Manisa")
        String taxOffice,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "IBAN; yalnızca ülke kodu ve son dört hane görünür",
                example = "TR** **** **** **** **** **13 26")
        String ibanMasked,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "ACTIVE ise ürünler satın alınabilir; PROVIDER_REJECTED ise bilgiler düzeltilmeli",
                example = "ACTIVE")
        PayoutAccountStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Son güncelleme anı (UTC)",
                example = "2026-10-08T09:30:00Z")
        Instant updatedAt) {
}

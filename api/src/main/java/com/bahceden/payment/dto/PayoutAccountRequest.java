package com.bahceden.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Satıcının ödeme bilgileri (seller-payout-details spec'i, add-cart-and-checkout D2).
 *
 * <p>Türe bağlı kurallar (TCKN mi VKN mi, vergi dairesi zorunlu mu) ve
 * sağlama denetimleri serviste yapılır ve ilgili alana bağlı hata döner. Tür
 * metin olarak alınıp kalıpla doğrulanır: tanınmayan değer okunamayan istek
 * değil, yerelleştirilmiş alan hatası olur.
 *
 * @param legalType satıcı türü
 * @param legalName yasal ad (kişi adı soyadı veya şirket unvanı)
 * @param taxId     TCKN (11 hane) veya VKN (10 hane)
 * @param taxOffice vergi dairesi; şahıs şirketi ve şirket için zorunlu
 * @param iban      TR IBAN; boşluklu yazılabilir
 */
@Schema(name = "PayoutAccountRequest", description = "Satıcının ödeme bilgileri")
public record PayoutAccountRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcı türü",
                allowableValues = {"INDIVIDUAL", "SOLE_PROPRIETOR", "COMPANY"}, example = "INDIVIDUAL")
        @NotBlank(message = "{validation.payoutLegalType.required}")
        @Pattern(regexp = "INDIVIDUAL|SOLE_PROPRIETOR|COMPANY", message = "{validation.payoutLegalType.allowed}")
        String legalType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Yasal ad: kişi adı soyadı veya şirket unvanı", example = "Örnek Üretici")
        @NotBlank(message = "{validation.legalName.required}")
        @Size(min = 2, max = 200, message = "{validation.legalName.length}")
        String legalName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "TCKN (bireysel ve şahıs şirketi, 11 hane) veya VKN (şirket, 10 hane)",
                example = "10000000078")
        @NotBlank(message = "{validation.taxId.required}")
        String taxId,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Vergi dairesi; şahıs şirketi ve şirket için zorunlu", example = "Manisa")
        @Size(max = 80, message = "{validation.taxOffice.length}")
        String taxOffice,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "TR IBAN; boşluklar yok sayılır",
                example = "TR33 0006 1005 1978 6457 8413 26")
        @NotBlank(message = "{validation.iban.required}")
        String iban) {
}

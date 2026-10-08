package com.bahceden.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Teslimat adresi (cart-and-checkout spec'i "Teslimat adresi yalnızca
 * teslimat yapılan illerden seçilir", D5). İlin teslimat listesinde olması ve
 * telefonun biçimi serviste denetlenir; hata ilgili alana bağlanır.
 *
 * @param title         adresin adı ("Ev", "İş")
 * @param recipientName teslim alacak kişi
 * @param phone         Türkiye cep veya sabit numarası
 * @param provinceCode  il plaka kodu; teslimat yapılan illerden biri
 * @param district      ilçe
 * @param addressLine   mahalle, sokak, bina ve daire
 * @param postalCode    isteğe bağlı posta kodu
 */
@Schema(name = "AddressRequest", description = "Teslimat adresi")
public record AddressRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Adresin adı", example = "Ev")
        @NotBlank(message = "{validation.addressTitle.required}")
        @Size(max = 40, message = "{validation.addressTitle.length}")
        String title,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Teslim alacak kişi", example = "Örnek Alıcı")
        @NotBlank(message = "{validation.recipientName.required}")
        @Size(min = 2, max = 120, message = "{validation.recipientName.length}")
        String recipientName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Telefon; kargo firması ulaşabilsin diye",
                example = "+90 555 000 00 00")
        @NotBlank(message = "{validation.phone.required}")
        String phone,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "İl plaka kodu; teslimat yapılan illerden biri (GET /api/v1/delivery/provinces)",
                example = "35")
        @NotBlank(message = "{validation.province.required}")
        String provinceCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İlçe", example = "Bornova")
        @NotBlank(message = "{validation.district.required}")
        @Size(max = 80, message = "{validation.district.length}")
        String district,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Mahalle, sokak, bina ve daire",
                example = "Ornek Mahallesi, Ornek Sokak No: 1 Daire: 2")
        @NotBlank(message = "{validation.addressLine.required}")
        @Size(min = 10, max = 250, message = "{validation.addressLine.length}")
        String addressLine,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "Posta kodu, 5 hane",
                example = "35040")
        @Pattern(regexp = "\\d{5}", message = "{validation.postalCode.format}")
        String postalCode) {
}

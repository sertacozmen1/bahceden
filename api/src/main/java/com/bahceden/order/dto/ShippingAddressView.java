package com.bahceden.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Siparişin teslimat adresi, sipariş anındaki kopya (D6). Yalnızca alıcıya
 * döner; satıcı yalnızca il ve ilçeyi görür (K10).
 *
 * @param recipientName teslim alacak kişi
 * @param phone         telefon
 * @param provinceCode  il plaka kodu
 * @param provinceName  il adı
 * @param district      ilçe
 * @param addressLine   açık adres
 * @param postalCode    posta kodu; yoksa {@code null}
 */
@Schema(name = "ShippingAddress", description = "Siparişin teslimat adresi")
public record ShippingAddressView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Teslim alacak kişi", example = "Örnek Alıcı")
        String recipientName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Telefon, E.164", example = "+905550000000")
        String phone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İl plaka kodu", example = "35")
        String provinceCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İl adı", example = "İzmir")
        String provinceName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İlçe", example = "Bornova")
        String district,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Açık adres",
                example = "Ornek Mahallesi, Ornek Sokak No: 1")
        String addressLine,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "Posta kodu",
                example = "35040")
        String postalCode) {
}

package com.bahceden.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Hesabın kayıtlı teslimat adresi; yalnızca sahibine döner (K10).
 *
 * @param id            adresin kimliği
 * @param title         adresin adı
 * @param recipientName teslim alacak kişi
 * @param phone         E.164 telefon
 * @param provinceCode  il plaka kodu
 * @param provinceName  il adı
 * @param district      ilçe
 * @param addressLine   açık adres
 * @param postalCode    posta kodu; yoksa {@code null}
 * @param deliverable   il bugün teslimat listesinde mi; değilse siparişte kullanılamaz
 */
@Schema(name = "AddressView", description = "Kayıtlı teslimat adresi")
public record AddressView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Adresin kimliği", example = "5")
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Adresin adı", example = "Ev")
        String title,
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
                example = "Ornek Mahallesi, Ornek Sokak No: 1 Daire: 2")
        String addressLine,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "Posta kodu",
                example = "35040")
        String postalCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "İl bugün teslimat listesinde mi; değilse siparişte kullanılamaz", example = "true")
        boolean deliverable) {
}

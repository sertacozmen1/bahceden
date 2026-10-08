package com.bahceden.seller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Satıcı başvurusu veya reddedilen başvurunun yeniden gönderimi
 * (seller-onboarding spec'i, add-seller-onboarding D3).
 *
 * <p>{@code multipart/form-data} olarak düz form alanlarıyla gelir. Belge türü
 * metin olarak alınır ve kalıpla doğrulanır: tanınmayan değer okunamayan istek
 * değil, yerelleştirilmiş bir alan hatası olur. Dosyanın türü ve boyutu Bean
 * Validation'ın göremediği kurallardır; servis katmanında içerikten doğrulanır.
 *
 * @param displayName  vitrinde görünecek üretici adı
 * @param city         üreticinin şehri; serbest metin (il listesi A4 kararına bağlı)
 * @param phone        iletişim telefonu; Türkiye numarası, E.164 biçimine çevrilir
 * @param documentType belge türü: {@code CKS_CERTIFICATE} veya {@code TAX_CERTIFICATE}
 * @param document     belge dosyası: PDF, JPEG veya PNG
 */
@Schema(name = "SellerApplicationRequest", description = "Satıcı başvurusu: beyan alanları ve belge dosyası")
public record SellerApplicationRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Vitrinde görünecek üretici adı", example = "Örnek Bahçe")
        @NotBlank(message = "{validation.sellerDisplayName.required}")
        @Size(min = 2, max = 160, message = "{validation.sellerDisplayName.length}")
        String displayName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Üreticinin şehri", example = "Manisa")
        @NotBlank(message = "{validation.sellerCity.required}")
        @Size(min = 2, max = 80, message = "{validation.sellerCity.length}")
        String city,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "İletişim telefonu; yalnızca admin görür. 0 veya +90 önekiyle yazılabilir",
                example = "+90 555 000 00 00")
        @NotBlank(message = "{validation.phone.required}")
        @Size(max = 20, message = "{validation.phone.format}")
        String phone,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Belge türü", allowableValues = {"CKS_CERTIFICATE", "TAX_CERTIFICATE"},
                example = "CKS_CERTIFICATE")
        @NotBlank(message = "{validation.documentType.required}")
        @Pattern(regexp = "CKS_CERTIFICATE|TAX_CERTIFICATE", message = "{validation.documentType.allowed}")
        String documentType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "binary",
                description = "Belge dosyası: PDF, JPEG veya PNG; en çok 10 MB")
        @NotNull(message = "{validation.document.required}")
        MultipartFile document) {
}

package com.bahceden.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Tüm hataların ortak gövdesi (AGENTS.md §11.5).
 *
 * <p>Tek bir şekil: istemci hata işlemeyi tek yerde yazar. {@code code} İngilizce
 * ve sabittir (istemci buna göre dallanır, dilden bağımsızdır); {@code message}
 * kullanıcıya gösterilebilir ve isteğin dilindedir (ADR-031).
 *
 * <p>Stack trace, SQL ve iç sınıf adı bu gövdeye <strong>sızmaz</strong>: hata
 * ayıklama bilgisi sunucu log'una yazılır, istemciye değil.
 *
 * @param code        makine tarafından okunan sabit hata kodu
 * @param message     kullanıcıya gösterilebilir açıklama, isteğin dilinde
 * @param fieldErrors alan bazlı doğrulama hataları; yoksa boş liste
 */
@Schema(name = "ApiError", description = "Hata yanıtı")
public record ApiError(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sabit hata kodu; dilden bağımsızdır",
                example = "VALIDATION_FAILED") String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Kullanıcıya gösterilebilir açıklama; isteğin diliyle (Accept-Language: tr, en) döner, varsayılan Türkçe",
                example = "Gönderilen değerler geçersiz.") String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alan bazlı doğrulama hataları") List<FieldError> fieldErrors) {

    public static ApiError of(ApiErrorCode code, String message) {
        return new ApiError(code.name(), message, List.of());
    }

    public static ApiError of(ApiErrorCode code, String message, List<FieldError> fieldErrors) {
        return new ApiError(code.name(), message, fieldErrors);
    }

    /**
     * @param field   hatalı alanın adı
     * @param message o alana ait hata açıklaması, isteğin dilinde
     */
    @Schema(name = "FieldError", description = "Alan bazlı doğrulama hatası")
    public record FieldError(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Hatalı alanın adı", example = "size") String field,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alana ait hata açıklaması; isteğin dilinde",
                    example = "Sayfa boyutu en az 1 olmalıdır") String message) {
    }
}

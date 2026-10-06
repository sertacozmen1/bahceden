package com.bahceden.common.web;

/**
 * Sabit hata kodları.
 *
 * <p>İstemci bu kodlara göre dallanır; Türkçe mesaj değiştiğinde istemci
 * davranışı bozulmaz. Liste yalnızca gerçekten üretilen kodları içerir: ileride
 * kullanılabilecek kodlar şimdiden eklenmez (AGENTS.md K6).
 */
public enum ApiErrorCode {

    /** İstek parametresi veya gövdesi doğrulamadan geçmedi (400). */
    VALIDATION_FAILED,

    /** Beklenmeyen sunucu hatası (500); ayrıntı yalnızca sunucu log'unda. */
    INTERNAL_ERROR
}

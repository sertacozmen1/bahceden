package com.bahceden.order.domain;

/**
 * Talebi uç duruma getiren taraf (ADR-080); raporlama içindir, kararı veren
 * hesap geçmiş tablosundadır.
 */
public enum DefectClaimResolver {

    /** Satıcı istenenin tamamını kabul etti. */
    SELLER,

    /** Alıcı teklifi veya reddi kabul etti. */
    BUYER,

    /** Süre işi: alıcı süresinde yanıt vermedi (ADR-083). */
    DEADLINE,

    /** Admin yönetime taşınan talebe karar verdi (ADR-080). */
    ADMIN
}

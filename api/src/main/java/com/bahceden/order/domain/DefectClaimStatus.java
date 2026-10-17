package com.bahceden.order.domain;

/**
 * Ayıplı ürün talebinin durumu (ADR-080). Geçişler ve kimin yapabileceği
 * talep servisindedir; uç durumlar {@link #REFUNDED} ve {@link #CLOSED}'dur.
 */
public enum DefectClaimStatus {

    /** Alıcı açtı; satıcının yanıtı bekleniyor (ADR-081). */
    OPEN,

    /** Satıcı daha az adet önerdi; alıcının yanıtı bekleniyor. */
    OFFERED,

    /** Satıcı gerekçeyle reddetti; alıcının yanıtı bekleniyor. */
    REJECTED,

    /** Admin kararı bekleniyor: alıcı taşıdı ya da satıcı süresinde yanıt vermedi. */
    ESCALATED,

    /** İade yapıldı (tam veya kısmi); uç durum. */
    REFUNDED,

    /** İadesiz kapandı; uç durum. */
    CLOSED
}

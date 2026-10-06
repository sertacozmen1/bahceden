package com.bahceden.catalog.domain;

/**
 * Ürünün satış birimi.
 *
 * <p>Serbest metin değil enum: fiyat karşılaştırması ve ileride kargo
 * hesaplaması birimin ne olduğunu bilmek zorunda. Liste bilinçli olarak kısa
 * tutuldu; yeni birim ihtiyacı doğarsa migration ve enum birlikte genişler
 * (design.md Open Questions).
 */
public enum ProductUnit {

    /** Kilogram cinsinden satılır. */
    KILOGRAM,

    /** Tane olarak satılır. */
    PIECE,

    /** Demet olarak satılır. */
    BUNCH
}

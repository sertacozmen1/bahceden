package com.bahceden.seller.domain;

/**
 * Başvuruda kabul edilen belge türleri (ADR-037 §3).
 *
 * <p>Belgenin yasal yeterliliği hukuk teyidi bekliyor; teyit farklı bir belge
 * isterse liste burada ve changeset kısıtında genişler, model değişmez.
 */
public enum SellerDocumentType {

    /** Çiftçi Kayıt Sistemi (ÇKS) belgesi. */
    CKS_CERTIFICATE,

    /** Vergi levhası. */
    TAX_CERTIFICATE
}

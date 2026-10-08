package com.bahceden.payment.domain;

/**
 * Satıcının ödeme sağlayıcısındaki türü (add-cart-and-checkout D2).
 *
 * <p>Tür hangi kimlik numarasının istendiğini ve vergi dairesinin zorunlu olup
 * olmadığını belirler: bireysel ve şahıs şirketi TCKN, şirket VKN verir.
 */
public enum PayoutLegalType {

    /** Bireysel satıcı: TCKN, vergi dairesi yok. */
    INDIVIDUAL(TaxIdKind.TCKN, false),

    /** Şahıs şirketi: sahibinin TCKN'si ve vergi dairesi. */
    SOLE_PROPRIETOR(TaxIdKind.TCKN, true),

    /** Limited veya anonim şirket: VKN ve vergi dairesi. */
    COMPANY(TaxIdKind.VKN, true);

    /** Kimlik numarasının türü. */
    public enum TaxIdKind {
        TCKN,
        VKN
    }

    private final TaxIdKind taxIdKind;
    private final boolean requiresTaxOffice;

    PayoutLegalType(TaxIdKind taxIdKind, boolean requiresTaxOffice) {
        this.taxIdKind = taxIdKind;
        this.requiresTaxOffice = requiresTaxOffice;
    }

    public TaxIdKind taxIdKind() {
        return taxIdKind;
    }

    public boolean requiresTaxOffice() {
        return requiresTaxOffice;
    }
}

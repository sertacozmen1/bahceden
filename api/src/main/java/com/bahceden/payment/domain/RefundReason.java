package com.bahceden.payment.domain;

/** İadenin nedeni (add-cart-and-checkout D8, D9; add-shipments D7). */
public enum RefundReason {

    /** Ödeme alındı ama stok yetmedi; ödemenin tamamı iade edilir. */
    OUT_OF_STOCK,

    /** Ödeme süresi dolduktan sonra doğrulandı; ödemenin tamamı iade edilir. */
    PAYMENT_EXPIRED,

    /** Satıcı siparişi kargodan önce iptal edildi; o satıcı payının tamamı iade edilir. */
    CANCELLED,

    /** Teslim edilemeyen satıcı siparişi için admin tam iade kararı verdi; o satıcı payının tamamı iade edilir. */
    UNDELIVERED
}

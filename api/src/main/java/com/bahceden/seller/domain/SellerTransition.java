package com.bahceden.seller.domain;

/**
 * Satıcı kaydının izinli durum geçişleri: tek yerde, adlandırılmış (ADR-037,
 * seller-onboarding spec'i).
 *
 * <p>Geçişi hedef durumla değil eylemle tanımlamak bilinçli: hem onay hem
 * askıdan çıkarma kaydı {@link SellerStatus#APPROVED} yapar, ama biri yalnızca
 * {@link SellerStatus#PENDING}, diğeri yalnızca {@link SellerStatus#SUSPENDED}
 * kayıttan yapılabilir. Hedef duruma göre genel bir geçiş tablosu bu farkı
 * kaybederdi.
 */
public enum SellerTransition {

    /** Sahibi reddedilen başvuruyu düzeltip yeniden gönderir. */
    RESUBMIT(SellerStatus.REJECTED, SellerStatus.PENDING, false),

    /** Admin başvuruyu onaylar; hesaba {@code ROLE_SELLER} eklenir. */
    APPROVE(SellerStatus.PENDING, SellerStatus.APPROVED, false),

    /** Admin başvuruyu gerekçeyle reddeder. */
    REJECT(SellerStatus.PENDING, SellerStatus.REJECTED, true),

    /** Admin onaylı satıcıyı gerekçeyle askıya alır; rol kaldırılır. */
    SUSPEND(SellerStatus.APPROVED, SellerStatus.SUSPENDED, true),

    /** Admin askıdaki satıcıyı askıdan çıkarır; rol geri eklenir. */
    REINSTATE(SellerStatus.SUSPENDED, SellerStatus.APPROVED, false);

    private final SellerStatus from;
    private final SellerStatus to;
    private final boolean requiresReason;

    SellerTransition(SellerStatus from, SellerStatus to, boolean requiresReason) {
        this.from = from;
        this.to = to;
        this.requiresReason = requiresReason;
    }

    public SellerStatus from() {
        return from;
    }

    public SellerStatus to() {
        return to;
    }

    /** Ret ve askı gerekçe ister; gerekçe satıcıya gösterilir. */
    public boolean requiresReason() {
        return requiresReason;
    }
}

# Ödeme ve Hakediş

**Durum:** Yayında
**İlk sürüm:** 2026-10-08
**Son değişiklik:** 2026-10-08

| | |
|---|---|
| **Ürün belgesi** | [Sepet ve Sipariş](../product/sepet-ve-siparis.md) (ödeme ve komisyon bölümleri) |
| **Kararlar** | Çerçeve: [ADR-054](../adr/054-split-payment-via-provider-port-provider-pending.md) (bölünmüş ödeme), [ADR-055](../adr/055-commission-percent-plus-fixed-from-seller.md) (komisyon), [ADR-056](../adr/056-cancel-before-shipment-and-defect-refund.md) (iptal ve iade). Uygulama: [ADR-060](../adr/060-seller-payout-account-validated-masked-sync-submerchant.md), [ADR-065](../adr/065-order-pricing-integer-minor-units-half-up.md), [ADR-066](../adr/066-payment-redirect-verify-lock-stock-on-success.md), [ADR-067](../adr/067-cancel-before-shipment-full-refund-restock.md) |
| **Spec (capability)** | `openspec/specs/seller-payout-details` (yeni); ödeme ve iade davranışı `cart-and-checkout` ve `orders` içinde |
| **Change'ler** | `openspec/changes/archive/2026-10-08-add-cart-and-checkout` (archive edildi) |
| **Referans** | [Domain modeli](../reference/01-domain-model.md), [API referansı](../reference/02-api-reference.md) |
| **Kod** | `api/src/main/java/com/bahceden/payment/` (`PaymentProvider`, `FakePaymentProvider`, `PaymentService`, `PayoutAccountService`, `PayoutIdentifiers`), `api/src/main/java/com/bahceden/order/OrderPricing.java`, `web/src/app/(seller)/seller/payout`, `web/src/features/payout` |

---

## Ne yapar

Onaylı üretici ödeme bilgilerini (satıcı türü, yasal ad, T.C. kimlik veya vergi numarası, IBAN) bir kez girer; numaralar sağlamalarıyla denetlenir ve ekranda yalnızca son dört haneyle gösterilir. Bilgiler ödeme kuruluşunda üreticinin adına bir alt hesap olarak açılır; bu yapılmadan üreticinin ürünleri satın alınamaz. Alıcının tek ödemesi her üreticinin payına bölünür: üreticiye ürün tutarından komisyon (%5 + sipariş başına 3 TL) düşülmüş tutar, pazaryerine komisyon ve kargo ücreti kalır. İptal ve stok yetersizliğinde para ödeme kuruluşundan geri ödenir ve her iade kaydedilir.

## Bilinçli sınırlar

- **Gerçek ödeme kuruluşu yok**: sahte sağlayıcı her işlemi onaylar, dışarı veri göndermez (KALANLAR A1).
- **Hakedişin üreticiye aktarım takibi ve vade ekranı yok.**
- **IBAN ve kimlik numarası veritabanında şifresiz** saklanır; yanıtta maskeli, log'da yok ([ADR-060](../adr/060-seller-payout-account-validated-masked-sync-submerchant.md); disk ve yedek şifrelemesi KALANLAR B5).
- **VKN'nin yalnızca biçimi** denetlenir (sağlama algoritması resmi değil).
- **Kampanya, satıcıya özel oran, taksit yok.**

## Açık konular

- Ödeme kuruluşu seçimi ve sözleşmesi (KALANLAR A1): komisyon/vade teklifi, alt üye onay süresi, sandbox, bildirim (webhook) biçimi.
- Aydınlatma metni ve saklama süresi (KALANLAR A5): IBAN, kimlik numarası ve sağlayıcıya veri aktarımı kapsamda.

## Tarihçe

- 2026-10-08: `add-cart-and-checkout` change'i kullanıcı onayıyla archive edildi (`openspec/changes/archive/2026-10-08-add-cart-and-checkout/`). Ön koşullar: delta spec'ler ana spec'lere sync edildi; D1–D10 → ADR-059…068; dokümanlar güncel; `api` 237 ve `web` 194 test yeşil. Taşımada göreli linkler bir seviye uzatıldı. Açık ve notlu: sipariş için e-posta doğrulama kapısı (`00-current-state` L11), hukuk teyidi (KALANLAR A5), gerçek cihaz doğrulaması.
- 2026-10-08: İlk sürüm (change `add-cart-and-checkout`).
  - `api` (`payment` paketi): satıcı ödeme hesabı (`/api/v1/sellers/me/payout-account`; TCKN, VKN, IBAN doğrulaması, maskeleme, eşzamanlı alt üye kaydı, sağlayıcı reddinde kaydı saklayıp 422); `PaymentProvider` arayüzü ve `FakePaymentProvider`; ödeme ve iade kayıtları.
  - Satın alınabilirlik koşuluna "satıcının ödeme hesabı etkin" eklendi ([ADR-059](../adr/059-payment-and-order-packages-single-purchasability-rule.md)).
  - Komisyon ve kargo ücreti `OrderPricing`'te, kuruş tamsayısıyla; değerler yapılandırmada (`bahceden.commerce`).
  - `web`: `/seller/payout`; satıcı panelinde sekmeler; ödeme hesabı etkin değilken ilan ekranlarında uyarı.
  - Kararlar: ADR-060, 065, 066, 067.

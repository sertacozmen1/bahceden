# Ayıplı Ürün ve İade

**Durum:** Yayında
**İlk sürüm:** 2026-10-14
**Son değişiklik:** 2026-10-14

| | |
|---|---|
| **Ürün belgesi** | [Ayıplı Ürün İadesi](../product/ayipli-urun-iadesi.md) |
| **Kararlar** | Çerçeve: [ADR-056](../adr/056-cancel-before-shipment-and-defect-refund.md), [ADR-055](../adr/055-commission-percent-plus-fixed-from-seller.md). Uygulama: [ADR-079](../adr/079-defect-claim-in-order-package-one-per-seller-order.md) … [ADR-086](../adr/086-defect-claim-web-surfaces.md) |
| **Spec (capability)** | `openspec/specs/defect-claims`; `orders` (talep bölümü, iade sonrası hakediş) |
| **Change'ler** | `openspec/changes/archive/2026-10-14-add-defect-claims` (archive edildi) |
| **Rehberler** | [Yerel geliştirme](../how-to/yerel-gelistirme.md) |
| **Kod** | `api/src/main/java/com/bahceden/order/` (`DefectClaim*`), `web/src/features/claims` |

---

## Ne yapar

Alıcı teslim edilen siparişte ezik, bozuk ya da eksik ürün görürse teslimden sonraki 48 saat içinde sorunlu ürünleri ve adetleri seçip fotoğrafla bildirir. Üretici talebi kabul eder, daha az adetle iade önerir ya da gerekçesini yazarak reddeder; kararın hakedişine etkisini önceden görür. Alıcı teklifi kabul eder ya da yönetime taşır. Taraflardan biri süresinde yanıt vermezse talep kendiliğinden ilerler. Anlaşamayan talepleri admin fotoğraflarıyla görüp karara bağlar. Para kartına iade edilir; ürün geri gönderilmez.

## Bilinçli sınırlar

- **Ürün geri gönderilmez** ve stok geri eklenmez ([ADR-082](../adr/082-defect-refund-amount-and-commission-recalculation.md)).
- **Satıcı siparişi başına tek talep** ([ADR-079](../adr/079-defect-claim-in-order-package-one-per-seller-order.md)); ikinci talep veya itiraz yok.
- **Kargo ücreti yalnızca tam iadede döner** (ADR-056 §5).
- **Admin'e bildirim yok**; admin listeyi izler ([ADR-085](../adr/085-defect-claim-emails.md)).
- **Ödeme sahte sağlayıcıyla**; gerçek sağlayıcıda kısmi iadenin pay bölüşümü açık (KALANLAR A1).

## Açık konular

- Gerçek sağlayıcıda kısmi iadenin satıcı ve pazaryeri payına bölünmesi (ADR-082 açık soru, KALANLAR A1).
- 48 saatlik pencere ve cayma istisnası metninin hukuk teyidi (KALANLAR A5).
- Talep fotoğraflarının saklama süresi (KALANLAR A5).

## Tarihçe

- 2026-10-14: `add-defect-claims` change'i kullanıcı onayıyla archive edildi (`openspec/changes/archive/2026-10-14-add-defect-claims/`). Ön koşullar: delta spec'ler ana spec'lere sync edildi (`defect-claims` yeni, `orders` değişti); D1–D8 → ADR-079…086; dokümanlar güncel; `api` 309 ve `web` 267 test yeşil. Taşımada göreli linkler bir seviye uzatıldı. Açık ve notlu: gerçek sağlayıcıda kısmi iadenin pay bölüşümü (KALANLAR A1), fotoğraf saklama süresi ve 48 saatlik sürenin hukuk teyidi (KALANLAR A5).
- 2026-10-14: `add-defect-claims` Faz 5: referans belgeleri (domain modeli, API referansı, güncel durum L37–L41), yerel geliştirme rehberi, KALANLAR A1 notu; delta spec'ler ana spec'lere sync edildi (`defect-claims` yeni, `orders` değişti). Uçtan uca elle doğrulama (`docker compose`): sipariş → gönderi → sahte entegratör teslim etti → alıcı 2 adet için fotoğraflı talep açtı → satıcı 1 adet önerdi (önizleme ₺34,50 iade, hakediş ₺29,77) → alıcı ekibe taşıdı → admin 2 adedi iade etti (₺168,00, kargo dahil; `refunds.reason = DEFECT`); altı talep e-postası mailpit'te. Admin ve alıcı ekranları 375 px koyu ve 1440 px açık temada görüntülendi, yatay taşma ve konsol hatası yok.
- 2026-10-13: `add-defect-claims` Faz 4: admin, yönetime taşınan ya da süresinde yanıtlanmayan talepleri `/admin/claims` ekranında görür; fotoğraflara ve iki tarafın beyanına bakıp satır başına satın alınan adede kadar iade eder (para etkisi sunucu önizlemesiyle) ya da gerekçeyle reddeder (ADR-080, ADR-086). Önizleme penceresi satıcı ve admin arasında ortak. `web` 267 test yeşil.
- 2026-10-13: `add-defect-claims` Faz 3: süre işi (Spring zamanlayıcısı, 5 dakikada bir): satıcı 48 saatte yanıt vermezse talep yönetime düşer; alıcı teklife sessiz kalırsa teklif uygulanır, redde sessiz kalırsa talep kapanır. Talep başına kilit ve yeniden denetim; iade hatasında talep değişmez ve sonraki turda denenir; eşzamanlı kabulde tek iade (ADR-083). `api` 302 test yeşil.
- 2026-10-13: `add-defect-claims` Faz 2: satıcı talebi tamamen kabul eder, adetli kısmi iade önerir (para etkisi sunucu önizlemesiyle) ya da gerekçeyle reddeder; alıcı teklifi veya reddi kabul eder ya da Bahçeden ekibine taşır. İade satıcı payından `DEFECT` nedeniyle; kısmi iadede komisyon kalan tutar üzerinden yeniden hesaplanır, tam iadede kargo da döner (ADR-082). E-postalar her adımda. `api` 294, `web` 254 test yeşil.
- 2026-10-13: `add-defect-claims` Faz 1: alıcı teslimden sonraki 48 saat içinde satır, adet, açıklama ve 1–3 fotoğrafla talep açar (`POST /api/v1/orders/{n}/seller-orders/{id}/claim`); satıcıya e-posta gider; alıcı ve satıcı talebi sipariş detayında görür; fotoğraflar yalnızca taraflara korumalı sunulur. Şema `014-defect-claims.sql`. `api` 279, `web` 241 test yeşil.
- 2026-10-13: `add-defect-claims` change'i önerildi ve onaylandı. Faz 0: D1–D8 → ADR-079…086; ürün belgesi yazıldı.

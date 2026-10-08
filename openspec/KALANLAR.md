# KALANLAR: Karar Bekleyen Konular

> **Amaç:** Ekip, ticari veya yasal karar bekleyen konuları **tek yerde** izlemek.
> Bir madde karara bağlandığında `docs/adr/` altına ADR yazılır ve madde buradan **silinir** (ADR numarası `docs/features/_genel.md` *Tarihçe*'sine düşer).
> Bu dosya `AGENTS.md` §15 ile eşleniktir; ikisi birlikte güncellenir.

**Kural:** Bir madde karara bağlanmadan ilgili alanda kod yazılmaz. İki alternatifi birlikte uygulamak (iki kimlik yolu, iki ödeme akışı) **yasaktır** (AGENTS.md §15).

---

## A. Ürün ve Ticari Kararlar

### A1 · Ödeme sağlayıcısı seçimi
**Soru:** Alt üye işyeri (pazaryeri) modelini destekleyen hangi lisanslı ödeme kuruluşu seçilecek (adaylar: iyzico Pazaryeri, PayTR Pazaryeri)? Komisyon ve vade teklifi, alt üye onay süresi, blokaj ve sandbox koşulları ne?
**Neden bloklayıcı:** Yalnızca gerçek sağlayıcı uygulamasını ve canlıda ödeme almayı bloklar. Sipariş ve ödeme durum makinesi `fake` sağlayıcıyla kurulabilir ([ADR-054](../docs/adr/054-split-payment-via-provider-port-provider-pending.md)).
**Kim karar verir:** Ürün sahibi + mali danışman.
**Durum:** Kısmen karara bağlandı (2026-10-08). Para akışı (bölünmüş ödeme), komisyon (%5 + 3 TL) ve iptal / iade kuralı → ADR-054…056. Sağlayıcı seçimi UNDECIDED.

### A2 · Kargo entegratörü seçimi
**Soru:** Hangi kargo entegratörüyle (tek API, çok firma) sözleşme yapılacak? Desi / bölge fiyatları, etiket biçimi, durum bildirimi (webhook mı, sorgu mu), sandbox koşulları ne?
**Neden bloklayıcı:** Yalnızca gerçek entegratör uygulamasını ve canlıda gönderi açmayı bloklar. `shipping` domaini `fake` entegratörle kurulabilir ([ADR-057](../docs/adr/057-shipping-via-integrator-port-integrator-pending.md)).
**Kim karar verir:** Ürün sahibi + operasyon.
**Durum:** Kısmen karara bağlandı (2026-10-08). Entegrasyon biçimi (entegratör API'si), kargo ücreti (satıcı başına 99 TL, 1000 TL ve üzeri ücretsiz, maliyet pazaryerinde) ve kargoya verme süresi (ilanda 1–3 iş günü) → ADR-057, ADR-058. Entegratör seçimi UNDECIDED.

### A5 · Satıcı verisinin saklama süresi ve aydınlatma metni
**Soru:** Satıcı başvurusunda toplanan belge (ÇKS belgesi veya vergi levhası) ve telefon ne kadar saklanır? Reddedilen ve yeniden gönderilen başvuruların eski belgeleri ne zaman silinir? Başvuru formunda KVKK aydınlatma metni ve açık rıza gerekiyor mu? ÇKS belgesi ve vergi levhası gıda satışı ve mesafeli satış için yeterli mi?
**Neden bloklayıcı:** Belge silme / anonimleştirme işi ve başvuru formundaki onay kutusu buna bağlı. Bugün belgeler silinmiyor ve formda aydınlatma metni yok ([ADR-037](../docs/adr/037-seller-application-with-document-and-manual-approval.md) açık riski, [ADR-045](../docs/adr/045-seller-documents-table-content-sniffing-protected-serving.md)).
**Kim karar verir:** Ürün sahibi + hukuk.
**Durum:** UNDECIDED. `add-seller-onboarding` change'i bu madde açıkken uygulandı; çıkan karar ayrı ve küçük bir dilim olur.

### A4 · MVP kapsam sınırları
**Soru:** Mobil uygulama yol haritasında mı? (Teslimat bölgesi karara bağlandı, aşağıda.)
**Neden bloklayıcı:** AGENTS.md §3 "over-engineering yapılmaz" ilkesinin sınırını bu cevap çizer; adres modeli buna göre kurulur.
**Kim karar verir:** Ürün sahibi.
**Durum:** Kısmen karara bağlandı. **Çoklu dil** sorusu 2026-10-07'de kapandı: MVP Türkçe + İngilizce, URL'de dil öneki yok (`docs/adr/029`…`032`, change `add-i18n`). **Teslimat bölgesi** 2026-10-08'de kapandı: başlangıçta İstanbul, İzmir, Ankara ve Manisa; liste yapılandırmada ([ADR-063](../docs/adr/063-delivery-provinces-config-address-book.md), change `add-cart-and-checkout`). Mobil uygulama sorusu UNDECIDED.

---

## B. Teknik Kararlar

### B4 · Bildirim kanalları
**Soru:** Sipariş/kargo bildirimleri yalnızca e-posta mı, SMS de var mı? SMS sağlayıcısı hangisi? Satıcıya yeni sipariş bildirimi hangi kanaldan gider?
**Neden bloklayıcı:** `notification` domaini ve K7 (SMS sağlayıcı entegrasyonu).
**Durum:** UNDECIDED.

### B5 · Üretim ortamı
**Soru:** Nerede host edilecek (tek VPS + Docker Compose mi, yönetilen platform mu)? Yedekleme stratejisi ne? Alan adı alındı mı? TLS nasıl sağlanacak?
**Neden bloklayıcı:** CI/CD iş akışları ve `docker-compose` üretim varyantı buna bağlı. MVP'de tek sunucu varsayılıyor (AGENTS.md §3).
**Durum:** UNDECIDED.

---

## Karara bağlananlar

Bu bölüm boş kaldıkça iyidir: karara bağlanan madde buradan **silinir**, ADR'ye ve `docs/features/_genel.md` *Tarihçe*'sine taşınır. İzini ADR tutar.

| Tarih | Madde | Sonuç |
|---|---|---|
| 2026-10-06 | Teknoloji yığını (framework, dil, veritabanı) | [ADR-001](../docs/adr/001-spring-api-and-next-web.md), [ADR-004](../docs/adr/004-postgresql-with-flyway.md) |
| 2026-10-06 | Bağımlılık bütçesi | [ADR-002](../docs/adr/002-dependency-budget.md) |
| 2026-10-06 | Katmanlar arası sözleşme biçimi | [ADR-003](../docs/adr/003-openapi-as-single-contract.md) |
| 2026-10-06 | Test stratejisi | [ADR-005](../docs/adr/005-test-strategy.md) |
| 2026-10-06 | Para birimi temsili | [ADR-006](../docs/adr/006-money-as-minor-unit.md) |
| 2026-10-06 | Satıcı veri izolasyonu | [ADR-007](../docs/adr/007-query-level-ownership-scoping.md) |
| 2026-10-06 | **B1** Kimlik doğrulama modeli: JWT erişim + opak/iptal edilebilir yenileme token'ı, tek hesap tablosu + roller, herkes için zorunlu e-posta doğrulaması, sosyal giriş kapsam dışı | [ADR-008](../docs/adr/008-jwt-auth-with-opaque-refresh.md) |
| 2026-10-06 | **B2** Sözleşme kaynağı: Spring kodundan üretilir; `openapi.yaml` üretilmiş ama commit'lenen çıktı, tazeliği CI'da zorunlu | [ADR-009](../docs/adr/009-contract-generated-from-spring.md) |
| 2026-10-08 | **A3** Satıcı onboarding: mevcut hesaptan başvuru, beyan (ad, şehir, telefon) + tek belge (ÇKS veya vergi levhası), manuel admin onayı; ilan ön onaysız yayına girer, admin gerekçeyle kaldırır. Belgenin yasal yeterliliği hukuk teyidi bekliyor (açık risk) | [ADR-037](../docs/adr/037-seller-application-with-document-and-manual-approval.md), [ADR-038](../docs/adr/038-listings-publish-immediately-with-admin-takedown.md) |
| 2026-10-08 | **A1** (kısmen) Para akışı bölünmüş ödeme / alt üye işyeri, sağlayıcı arayüz arkasında ve `fake` ile; komisyon satıcıdan %5 + satıcı siparişi başına 3 TL; kargo öncesi iptal tam iade, teslim sonrası cayma yok, ayıplı ürün 48 saat fotoğraflı talep, kısmi iade, anlaşmazlıkta admin. Sağlayıcı seçimi açık | [ADR-054](../docs/adr/054-split-payment-via-provider-port-provider-pending.md), [ADR-055](../docs/adr/055-commission-percent-plus-fixed-from-seller.md), [ADR-056](../docs/adr/056-cancel-before-shipment-and-defect-refund.md) |
| 2026-10-08 | **A2** (kısmen) Gönderi kargo entegratörü API'siyle, entegratör arayüz arkasında ve `fake` ile; satıcı başına sabit 99 TL, satıcının ürün toplamı 1000 TL ve üzeriyse ücretsiz, gerçek maliyet pazaryerinde; kargoya verme süresi ilanda 1–3 iş günü beyan. Entegratör seçimi açık | [ADR-057](../docs/adr/057-shipping-via-integrator-port-integrator-pending.md), [ADR-058](../docs/adr/058-flat-shipping-fee-per-seller-and-declared-handover.md) |
| 2026-10-08 | **A4** (kısmen) Teslimat bölgesi: başlangıçta İstanbul, İzmir, Ankara, Manisa; liste yapılandırmada, il kataloğu sabit. Mobil uygulama sorusu açık | [ADR-063](../docs/adr/063-delivery-provinces-config-address-book.md) |
| 2026-10-08 | **B3** Dosya depolama: Docker volume'da yerel disk, `api` sunar, ImageIO ile yeniden boyutlandırma ve EXIF temizliği; belgeler yalnızca yetkili uçtan; CDN yok | [ADR-039](../docs/adr/039-uploads-on-local-volume-served-by-api.md) |

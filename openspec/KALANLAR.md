# KALANLAR: Karar Bekleyen Konular

> **Amaç:** Ekip, ticari veya yasal karar bekleyen konuları **tek yerde** izlemek.
> Bir madde karara bağlandığında `docs/adr/` altına ADR yazılır ve madde buradan **silinir** (ADR numarası `docs/features/_genel.md` *Tarihçe*'sine düşer).
> Bu dosya `AGENTS.md` §15 ile eşleniktir; ikisi birlikte güncellenir.

**Kural:** Bir madde karara bağlanmadan ilgili alanda kod yazılmaz. İki alternatifi birlikte uygulamak (iki kimlik yolu, iki ödeme akışı) **yasaktır** (AGENTS.md §15).

---

## A. Ürün ve Ticari Kararlar

### A1 · Ödeme ve para akışı
**Soru:** Sanal POS / ödeme sağlayıcısı hangisi? Pazaryeri modeli alt üye işyeri (marketplace payout) mu, tek havuz hesap + manuel hakediş mi? Komisyon oranı ne, kimden kesilir? İade ve kısmi iade kuralı ne?
**Neden bloklayıcı:** Sipariş durum makinesi, `payment` domaininin tamamı ve para alanları buna bağlı.
**Kim karar verir:** Ürün sahibi + mali danışman.
**Durum:** UNDECIDED.

### A2 · Kargo ve teslimat
**Soru:** Anlaşmalı kargo firması/firmaları hangileri? Entegrasyon API ile mi, satıcının elle girdiği takip numarası ile mi? Kargo ücretini alıcı mı satıcı mı öder, eşiğe bağlı mı? Bozulabilir ürün için teslim süresi taahhüdü veriliyor mu, verilemezse sorumluluk kimde?
**Neden bloklayıcı:** `shipping` domaini, satıcı akışının en kritik ekranı ve iptal/iade kuralları buna bağlı.
**Kim karar verir:** Ürün sahibi + operasyon.
**Durum:** UNDECIDED.

### A5 · Satıcı verisinin saklama süresi ve aydınlatma metni
**Soru:** Satıcı başvurusunda toplanan belge (ÇKS belgesi veya vergi levhası) ve telefon ne kadar saklanır? Reddedilen ve yeniden gönderilen başvuruların eski belgeleri ne zaman silinir? Başvuru formunda KVKK aydınlatma metni ve açık rıza gerekiyor mu? ÇKS belgesi ve vergi levhası gıda satışı ve mesafeli satış için yeterli mi?
**Neden bloklayıcı:** Belge silme / anonimleştirme işi ve başvuru formundaki onay kutusu buna bağlı. Bugün belgeler silinmiyor ve formda aydınlatma metni yok ([ADR-037](../docs/adr/037-seller-application-with-document-and-manual-approval.md) açık riski, [ADR-045](../docs/adr/045-seller-documents-table-content-sniffing-protected-serving.md)).
**Kim karar verir:** Ürün sahibi + hukuk.
**Durum:** UNDECIDED. `add-seller-onboarding` change'i bu madde açıkken uygulandı; çıkan karar ayrı ve küçük bir dilim olur.

### A4 · MVP kapsam sınırları
**Soru:** Teslimat bölgesi sınırlı mı (tek il / bölge) yoksa Türkiye geneli mi? Mobil uygulama yol haritasında mı?
**Neden bloklayıcı:** AGENTS.md §3 "over-engineering yapılmaz" ilkesinin sınırını bu cevap çizer; adres modeli buna göre kurulur.
**Kim karar verir:** Ürün sahibi.
**Durum:** Kısmen karara bağlandı. **Çoklu dil** sorusu 2026-10-07'de kapandı: MVP Türkçe + İngilizce, URL'de dil öneki yok (`docs/adr/029`…`032`, change `add-i18n`). Teslimat bölgesi ve mobil uygulama soruları UNDECIDED.

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
| 2026-10-08 | **B3** Dosya depolama: Docker volume'da yerel disk, `api` sunar, ImageIO ile yeniden boyutlandırma ve EXIF temizliği; belgeler yalnızca yetkili uçtan; CDN yok | [ADR-039](../docs/adr/039-uploads-on-local-volume-served-by-api.md) |

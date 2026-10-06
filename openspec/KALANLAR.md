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

### A3 · Satıcı onboarding ve doğrulama
**Soru:** Üretici nasıl doğrulanır (vergi levhası, çiftçi kayıt belgesi, TC doğrulama)? Onayı kim verir (manuel admin onayı mı, otomatik mi)? Ürün ilanı yayına girmeden ön onaydan geçer mi?
**Neden bloklayıcı:** `seller` ve `catalog` durum makineleri; yasal sorumluluk (mesafeli satış, gıda satışı mevzuatı).
**Kim karar verir:** Ürün sahibi + hukuk.
**Durum:** UNDECIDED.

### A4 · MVP kapsam sınırları
**Soru:** Teslimat bölgesi sınırlı mı (tek il / bölge) yoksa Türkiye geneli mi? Çoklu dil MVP'de var mı? Mobil uygulama yol haritasında mı?
**Neden bloklayıcı:** AGENTS.md §3 "over-engineering yapılmaz" ilkesinin sınırını bu cevap çizer; adres modeli ve metin katmanı buna göre kurulur.
**Kim karar verir:** Ürün sahibi.
**Durum:** UNDECIDED.

---

## B. Teknik Kararlar

### B1 · Kimlik doğrulama modeli
**Soru:** `HttpOnly` oturum çerezi mi, JWT mi? Satıcı ve alıcı aynı hesap tablosunda mı yoksa ayrı mı? E-posta doğrulaması kayıt için zorunlu mu? Sosyal giriş (Google) MVP'de var mı?
**Neden bloklayıcı:** Her endpoint'in yetkilendirmesi, `web` oturum akışı ve CSRF/CORS yapılandırması buna bağlı. İlk dilimden önce karara bağlanmalı.
**Öneri (tartışmaya açık):** Tek `account` tablosu + rol; `HttpOnly` + `Secure` + `SameSite=Lax` oturum çerezi (tarayıcıda token tutmamak XSS yüzeyini küçültür); e-posta doğrulaması satıcı için zorunlu, alıcı için ilk siparişe kadar erteli.
**Durum:** UNDECIDED.

### B2 · Sözleşme kaynağı (contract-first yönü)
**Soru:** `shared/contracts/openapi.yaml` elle mi yazılır (sözleşme kaynak, kod ona uyar) yoksa Spring anotasyonlarından mı üretilir (kod kaynak, sözleşme çıktı)?
**Neden bloklayıcı:** AGENTS.md K2/K4 ve §7 komutları buna bağlı; K8 "üretilmiş dosyaya dokunma" kuralının hangi dosyaya uygulandığını bu karar belirler.
**Öneri (tartışmaya açık):** Elle yazılan `openapi.yaml` (AGENTS.md §1 felsefesiyle tutarlı: sözleşme tek doğruluk kaynağı); `api` tarafında sözleşmeye karşı doğrulama testi, `web` tarafında tip üretimi.
**Durum:** UNDECIDED — karar verilince ADR-008 yazılır.

### B3 · Ürün fotoğrafı depolama
**Soru:** Fotoğraflar nerede saklanır: yerel disk + volume mi, S3 uyumlu nesne depolama mı? Yeniden boyutlandırma sunucuda mı yapılır? CDN var mı?
**Neden bloklayıcı:** `catalog` domaini ve K7 bağımlılık bütçesi (nesne depolama istemcisi yeni paket demektir).
**Durum:** UNDECIDED.

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

# [ADR-039] Yüklenen dosyalar yerel diskte (Docker volume) durur, `api` sunar; boyutlandırma ImageIO ile

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `openspec/KALANLAR.md` B3 (ürün fotoğrafı depolama); ürün sahibinin 2026-10-08 kararı

---

## Context

İki tür dosya yüklenecek:

- **Ürün fotoğrafları** herkese açıktır. Vitrinde ve ürün detayında gösterilir.
- **Satıcı başvuru belgeleri** gizlidir. Yalnızca admin görür ([ADR-037](037-seller-application-with-document-and-manual-approval.md)).

Kısıtlar:

- MVP tek sunucu ve Docker Compose ile çalışıyor (`AGENTS.md` §3). Barındırma ve yedekleme kararı açık (KALANLAR B5).
- Nesne depolama istemcisi (AWS SDK vb.) ve yeni bir Docker imajı (MinIO) bağımlılık bütçesinin dışında kalır ve ayrı onay gerektirir (K7, [ADR-002](002-dependency-budget.md)).
- `AGENTS.md` §11.6: tip ve boyut sunucuda doğrulanır, dosya adı yeniden üretilir, içerik uygulama kökünden servis edilmez.
- `web` yalnızca `api` üzerinden veri alır. Dış servise kendi anahtarıyla gitmez (§3).

## Decision

1. **Dosyalar `api` konteynerine bağlanan adlandırılmış bir Docker volume'unda saklanır.** Dizin yolu ortam değişkeninden okunur (`@ConfigurationProperties`, açılışta doğrulanır). Dizin, uygulama kökü ve sınıf yolu dışındadır.
2. **Dosya adı sunucuda üretilir** (rastgele, tahmin edilemez kimlik). Kullanıcının verdiği ad diske ve adrese yansımaz. Veritabanı yalnızca bu kimliği ve meta veriyi tutar: tür, boyut, sahip, oluşturulma zamanı.
3. **Doğrulama sunucuda yapılır.** İzin verilen tipler sabit bir beyaz listededir. Tip, dosya içeriğindeki imzaya bakılarak belirlenir; istemcinin gönderdiği `Content-Type`'a güvenilmez. Boyutun üst sınırı yapılandırmadadır. Kesin değerler ilgili change'de belirlenir.
4. **Fotoğraflar yüklenirken sunucuda yeniden boyutlandırılır** ve sabit bir kaç boyutta yeniden kodlanır. Bunun için JDK'nin `javax.imageio` paketi kullanılır; yeni paket yoktur. Yeniden kodlama, orijinal dosyanın meta verisini (konum bilgisi dahil EXIF) de atar.
5. **Ürün fotoğrafları `api`'nin herkese açık bir ucundan sunulur.** Yanıt uzun süreli önbellek başlığı taşır; dosya kimliği değişmez olduğu için bu güvenlidir. `web` bu adresi `next/image` ile kullanır.
6. **Başvuru belgeleri yalnızca yetkili bir uçtan sunulur.** Bu uç `ROLE_ADMIN` ve belgenin sahibi olan hesap içindir; sahiplik sorgu seviyesinde kontrol edilir ([ADR-007](007-query-level-ownership-scoping.md)). Belgelerin herkese açık bir adresi yoktur. Belgeler yeniden kodlanmaz, olduğu gibi saklanır.
7. CDN yoktur. Erişim ve yedekleme stratejisi B5 kararıyla belirlenir.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| S3 uyumlu nesne depolama (geliştirmede MinIO, üretimde S3/R2) | Yeni paket ve yeni Docker imajı gerektirir (K7). Tek sunuculu MVP'de ölçek ihtiyacı yok; "ileride lazım olur" gerekçe değildir (§3). |
| Dosyayı PostgreSQL'de `bytea` olarak saklamak | Veritabanı ve yedekler şişer, her görsel isteği DB'ye gider. Disk + meta veri ayrımı daha basit. |
| Boyutlandırmayı istemcide yapmak | Sunucu istemciye güvenemez (`AGENTS.md` §11.1). EXIF temizliği de istemciye kalırdı. |
| Next.js görsel optimizasyonuna bırakıp orijinali saklamak | Büyük orijinaller diskte kalır ve EXIF temizlenmez. `next/image` optimizasyonu yine kullanılabilir, ama kaynak dosya zaten küçük ve temiz olmalıdır. |
| Görüntü işleme kütüphanesi (Thumbnailator, imgscalr vb.) | ImageIO, JPEG ve PNG'yi yeniden boyutlandırmaya yeter. Yeni paket K7 onayı gerektirir. |

## Consequences

### Olumlu
- Yeni bağımlılık ve yeni servis gerekmez. Geliştirme ortamı aynı kalır, compose dosyasına yalnızca bir volume eklenir.
- Fotoğraflar küçük ve konum bilgisinden arındırılmış olarak saklanır. Ayrıca bir KVKK önlemi gerekmez.
- Gizli belge ile herkese açık fotoğraf ayrı uçlardan sunulur. Belgeye yanlışlıkla herkese açık erişim verme yolu yoktur.

### Olumsuz
- Dosyalar tek bir sunucunun diskine bağlıdır. `api` ikinci bir örneğe çıkarsa paylaşılan depolama gerekir; o gün bu karar yeniden değerlendirilir.
- Yedekleme artık hem veritabanını hem volume'u kapsamak zorundadır (B5).
- ImageIO'nun WebP ve HEIC desteği yoktur. Telefonların varsayılan HEIC çıktısı kabul edilmez; kullanıcıya JPEG veya PNG yüklemesi söylenir. Bu kısıt ihtiyaç doğarsa ayrı bir K7 kararıdır.
- Görsel istekleri `api` üzerinden geçer ve önünde CDN yoktur. MVP trafiği için yeterlidir.

### Nötr / Trade-off
- Disk yolu ve boyut sınırı yapılandırmadadır. Diske okuma/yazma tek bir sınıfta toplanır; nesne depolamaya geçiş o sınıfın değişmesidir. Bugün bunun için ayrı bir soyutlama katmanı yazılmaz (§3).

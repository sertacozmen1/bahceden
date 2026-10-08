# Design: add-seller-onboarding

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2): D1 → ADR-040, D2 → ADR-041, D3 → ADR-042, D4 → ADR-043, D5 → ADR-044, D6 → ADR-045, D7 → ADR-046.
> Çerçeve kararlar zaten alındı: [ADR-037](../../../docs/adr/037-seller-application-with-document-and-manual-approval.md) (beyan + belge, manuel onay, durumlar), [ADR-038](../../../docs/adr/038-listings-publish-immediately-with-admin-takedown.md) (onaylı olmayan satıcının ilanı görünmez), [ADR-039](../../../docs/adr/039-uploads-on-local-volume-served-by-api.md) (yerel volume, `api` sunar). Aşağıdaki kararlar bunların uygulama ayrıntılarını sabitler ve hiçbirine aykırı değildir.

## Kararlar

### D1 · Başvuru, satıcı kaydının kendisidir; durum geçişleri ayrı ve silinmeyen bir tabloda tutulur

Ayrı bir `seller_applications` tablosu yoktur. Başvuru, `sellers` tablosunda `PENDING` durumunda açılan bir satırdır. `sellers` tablosuna şu kolonlar eklenir:

- `account_id`: tekil; NULL olabilir, çünkü seed'den gelen satıcıların hesabı yok;
- `status`: `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`;
- `phone`;
- `status_reason`: son ret veya askı gerekçesi; satıcıya gösterilir;
- `status_changed_at`.

Her geçiş `seller_status_changes` tablosuna bir satır olarak yazılır. Satırda şunlar bulunur: `seller_id`, `from_status` (başvuruda NULL), `to_status`, `reason`, `actor_account_id`, `created_at`. Bu tablo yalnızca eklenerek büyür; uygulama kodunda güncelleme veya silme yolu yoktur.

`Seller` entity'si `catalog.domain`'den yeni `com.bahceden.seller` paketine taşınır (`AGENTS.md` §11.2 domain listesinde `seller` ayrı bir domaindir). `catalog`, `Product → Seller` ilişkisi üzerinden bu sınıfa bağlı kalır.

**Gerekçe:**
- Kayıt ile başvuru arasında 1:1 ilişki var ve yeniden gönderim aynı kaydı günceller (ADR-037 §6). İki tablo, aynı satıcının iki farklı "durum" kaynağı olması demek olurdu.
- Vitrin, sonraki change'te satıcı paneli ve admin listesi aynı satırı okur. Durum tek yerdedir.
- Denetim izi (ADR-037 §7) ayrı ve yalnızca eklenen bir tabloda tutulduğu için güncel durum sorguları sade kalır, geçmiş de değiştirilemez.

**Reddedilen alternatifler:**
- (a) Ayrı `seller_applications` tablosu ve onayda `sellers` satırının kopyalanması: veri iki yerde durur, yeniden gönderimde hangisinin geçerli olduğu belirsizleşir.
- (b) Geçmişi `sellers` satırındaki kolonlarda tutmak (`approved_by`, `rejected_at` …): yalnızca son geçişi tutar, geçmiş kaybolur.
- (c) `Seller`'ı `catalog` içinde bırakmak: satıcı yaşam döngüsü katalogdan büyük bir domain. Paket düzeni kuralı (§11.2) taşımayı istiyor; taşıma tek sınıftır.

### D2 · Mevcut satıcılar `APPROVED` olur; vitrin satıcı durumunu sorguda filtreler

Changeset, `status` kolonunu `APPROVED` varsayılanıyla ekler. Böylece mevcut satırlar (yalnızca dev seed'i) onaylı olur. Ardından varsayılan kaldırılır: yeni kayıtların durumunu uygulama açıkça yazar.

`ProductRepository.findPublished` ve sayım sorgusu `p.seller.status = APPROVED` koşulunu da taşır. Vitrin indeksi değişmez. MVP hacminde `products_seller_idx` ve satıcı birincil anahtarı birleştirme için yeterli; ölçüm aksini gösterirse indeks ayrı bir changeset olur.

**Gerekçe:**
- ADR-038 §4: onaylı olmayan satıcının ilanı vitrinde görünmez. Bu kural ürünün kendi durumuna yazılırsa, askıdan çıkarmada ürünlerin eski durumlarını hatırlamak gerekir. Sorgu filtresi geri dönüşü kendiliğinden doğru yapar.
- Varsayılanın kaldırılması, ileride yazılacak bir kodun durumu unutup satıcıyı sessizce onaylı açmasını engeller.

**Reddedilen alternatifler:**
- (a) Askıya almada satıcının ürünlerini `ARCHIVED`'a çekmek: askıdan çıkarmada hangi ürünün önceden yayında olduğu bilinmez, ayrıca toplu yazma gerekir.
- (b) Mevcut satırları `PENDING` yapmak: dev vitrini boşalır, seed satıcılarının onaylayacak bir hesabı da yok.

### D3 · Başvuru tek bir multipart istektir; yeniden gönderim de öyle

- `POST /api/v1/sellers/me` kaydı oluşturur.
- `PUT /api/v1/sellers/me` reddedilen kaydı yeniden gönderir.

İkisi de `multipart/form-data` alır ve düz form alanlarından oluşur: `displayName`, `city`, `phone`, `documentType` ve dosya olarak `document`. Alanlar `@ModelAttribute` ile tek bir istek kaydına bağlanır. Bean Validation bütün alanlarda, dosyanın varlığı dahil, çalışır. `documentType` metin olarak alınır ve izinli değerler kalıbıyla doğrulanır. Böylece tanınmayan bir değer, okunamayan istek (500) yerine alan bazlı doğrulama hatası (400) olur.

Kayıt ve dosya tek bir işlem olarak ele alınır. Doğrulama hatasında hiçbir şey yazılmaz. Dosya diske yazılıp veritabanı transaction'ı geri alınırsa, yazılan dosya silinir.

> Uygulama notu (2026-10-08): ilk taslakta iki parça vardı, JSON olarak `application` ve dosya olarak `document`. Ancak JSON parçasındaki tanınmayan bir enum değeri okunamayan istek olarak 500'e düşüyor ve yerelleştirilmiş alan mesajı üretilemiyordu. Bu yüzden düz form alanlarına geçildi. Kararın özü, yani tek istek, değişmedi.

**Gerekçe:**
- Satıcı için en kısa yol tek ekran ve tek gönderimdir (§12.5). Belgesiz bir başvuru admin için anlamsız (ADR-037 §3).
- İki adımlı akışta (önce kayıt, sonra yükleme) belgesi hiç gelmeyen yetim kayıtlar ve sahipsiz dosyalar oluşur, bunları temizlemek için ayrı bir iş gerekir.

**Reddedilen alternatifler:**
- (a) Önce JSON ile kayıt, sonra ayrı bir uçla belge yükleme: yetim kayıt ve sahipsiz dosya oluşur, istemci iki hatayı ayrı yönetir.
- (b) Belgeyi Base64 ile JSON içinde taşımak: boyut yaklaşık üçte bir artar, bellekte tamamen tutulur ve sözleşmede ikili alan anlamsızlaşır.
- (c) Beyanı JSON parçası, belgeyi dosya parçası olarak göndermek: tanınmayan alan değerleri okunamayan istek olur ve yerelleştirilmiş alan hatası üretilemez (yukarıdaki uygulama notu).

### D4 · Başvuru için e-posta doğrulaması zorunludur

E-postası doğrulanmamış hesap başvuramaz: **422** `SELLER_EMAIL_NOT_VERIFIED` döner. Arayüz kullanıcıyı mevcut "yeni doğrulama bağlantısı" eylemine yönlendirir ([ADR-026](../../../docs/adr/026-verification-link-resend-requires-session.md)).

**Gerekçe:**
- Admin kararları e-postayla bildirilir. Doğrulanmamış bir adrese giden karar kaybolabilir.
- [ADR-017](../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md) doğrulamayı *girişin* ön koşulu olmaktan çıkardı ve bunu bilinçli bir borç olarak kabul etti (L11). Satıcılık, para ve yasal sorumluluk taşıyan ilk eylem; kapının uygulandığı ilk yer de burası. Bu, ADR-017'ye aykırı değildir: giriş hâlâ engellenmez.

**Reddedilen alternatifler:**
- (a) Doğrulamayı onay anında admin'e bırakmak: admin'e mekanik bir kontrol yükler ve kararın bildirimi yine kaybolabilir.
- (b) Kapı koymamak: bildirimin teslim edileceği bir güvence kalmaz.

### D5 · `ROLE_SELLER` onayda eklenir, askıda kaldırılır; satıcıya özgü sorgular ayrıca `APPROVED` durumunu denetler

| Geçiş | Rol |
|---|---|
| `PENDING` → `APPROVED` | `ROLE_SELLER` eklenir |
| `APPROVED` → `SUSPENDED` | `ROLE_SELLER` kaldırılır |
| `SUSPENDED` → `APPROVED` | `ROLE_SELLER` yeniden eklenir |
| Ret ve yeniden gönderim | rol değişmez |

Rol ve durum aynı transaction içinde değişir. Erişim token'ı rolleri her yenilemede güncel kayıttan okur (`SessionService.issueTokens`). Bu yüzden değişiklik en geç 15 dakika içinde token'a yansır. Oturumlar kapatılmaz, çünkü kullanıcı alıcı olarak devam edebilir.

Rolün token'dan düşmesi gecikebildiği için `seller` paketi tek bir kapı sunar: `SellerService.requireApprovedSeller(accountId)`. Kapı, onaylı satıcıyı `findByAccountIdAndStatus(accountId, APPROVED)` sorgusuyla getirir; bulamazsa 403 `SELLER_NOT_APPROVED` döner. Sonraki change'lerde satıcıya özgü her işlem bu kapıdan geçer ve kendi sahiplik sorgusunu bu satıcının kimliğiyle kurar ([ADR-007](../../../docs/adr/007-query-level-ownership-scoping.md)). Bu change'te kapı yazılır ve doğrudan `critical` testle sabitlenir. Onu kullanan ilk uç ilan change'iyle gelir.

**Gerekçe:**
- Yetkilendirme kararı token'daki rolle verilir; her istekte veritabanına gidilmez ([ADR-008](../../../docs/adr/008-jwt-auth-with-opaque-refresh.md), add-account-and-session D7). Rol "şu an satış yapabilir" anlamına gelirse bu model korunur.
- Askı güvenlik niteliğinde bir eylemdir; 15 dakikalık pencere kabul edilemez. Satıcının kimliği zaten sorguyla bulunuyor, aynı sorguya durum koşulunu eklemenin ek maliyeti yok.

**Reddedilen alternatifler:**
- (a) Rolü yalnızca onayda vermek, askıda bırakmak ve kararı tamamen duruma bırakmak: token'daki rol yanlış bir şey söyler, `web` satıcı menüsünü askıdaki satıcıya da gösterir.
- (b) Askıda hesabın bütün oturumlarını kapatmak: kullanıcı alıcı olarak da dışarı atılır. Bunun yanında erişim token'ı yine 15 dakika geçerli kalır, yani sorun çözülmez.
- (c) Her istekte rolleri veritabanından okumak: ADR-008'in durumsuz modelini bütün uçlar için değiştirir.

### D6 · Belge `seller_documents` tablosunda; tür içerikten tespit edilir; yalnızca sahibine ve admin'e, korumalı başlıklarla sunulur

- **Saklama:** her başvuru ve yeniden gönderim, `seller_documents` tablosuna yeni bir satır yazar. Satırda şunlar bulunur: `seller_id`, `document_type`, `storage_key` (sunucunun ürettiği rastgele ad), `content_type`, `size_bytes`, `created_at`. Kaydın güncel belgesi en son satırdır. Önceki belgeler silinmez: hangi belgeyle onay verildiği denetim izinin parçasıdır (ADR-037 §7).
- **Disk erişimi:** `common/storage/FileStorage` adlı tek bir sınıf üzerinden yapılır: yazma, okuma, silme ve türün içerikten tespiti. Kök dizin `bahceden.storage.dir` ayarından gelir (`BAHCEDEN_STORAGE_DIR`). Açılışta dizinin var olduğu ve yazılabilir olduğu doğrulanır; değilse uygulama başlamaz. Soyutlama katmanı yoktur (ADR-039).
- **Tür tespiti:** dosyanın ilk baytlarına bakılır: PDF `%PDF-`, JPEG `FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`. İstemcinin gönderdiği `Content-Type` ve dosya adı yok sayılır.
- **Boyut:** üst sınır `bahceden.storage.max-document-bytes` ayarındadır, varsayılan 10 MB. Spring'in multipart sınırı da buna göre ayarlanır, böylece sınırı aşan istek belleğe alınmadan reddedilir.
- **Sunum:** belge iki uçtan sunulur: sahibine `GET /api/v1/sellers/me/document`, admin'e `GET /api/v1/admin/sellers/{id}/document`. Yanıt başlıkları: tespit edilen `Content-Type`, `X-Content-Type-Options: nosniff`, `Content-Security-Policy: sandbox`, `Cache-Control: private, no-store`, sunucunun ürettiği bir adla `Content-Disposition: inline`. Belgenin herkese açık bir adresi yoktur.

**Gerekçe:**
- Ürün fotoğrafları herkese açık, boyutlandırılmış ve farklı yaşam döngüsüne sahip olacak (ADR-039). Belgeyle aynı tabloyu paylaşmak, her sorguyu "hangi tür" süzgecine bağlar ve süzgeç unutulduğunda gizli belge herkese açık uçtan sunulur. Ayrı tablo bu hatayı şema düzeyinde imkânsız kılar.
- İmza kontrolü yeni paket gerektirmez ve kabul edilen üç tür için yeterlidir.
- `sandbox` ve `nosniff` başlıkları, belge aynı kökenden açılsa bile içindeki betiklerin çalışmasını ve tarayıcının türü yeniden yorumlamasını engeller.

**Reddedilen alternatifler:**
- (a) Genel bir `stored_files` tablosu: herkese açık ve gizli dosyalar aynı yerde durur, yukarıdaki süzgeç riski doğar.
- (b) Yeniden gönderimde eski belgeyi silmek: daha az kişisel veri tutulur, ama onayın dayandığı belge kaybolur. Saklama süresi ayrı bir hukuk kararıdır (Open Questions).
- (c) Apache Tika ile tür tespiti: K7 dışında büyük bir bağımlılık, üç tür için gereksiz.
- (d) Belgeyi her zaman `attachment` olarak indirtmek: admin her belgeyi indirip ayrı açmak zorunda kalır. Korumalı başlıklarla `inline` gösterim güvenli.

### D7 · Admin yüzeyi `web` içinde `(admin)` rota grubunda; ilk admin veritabanından atanır

- **Sayfalar:** `/admin/sellers` ve `/admin/sellers/[id]`, `web/src/app/(admin)/` altında. Kapı `requireRole("ROLE_ADMIN")`: oturum yoksa giriş, rol yoksa bulunamadı sayfası. Admin yüzeyinin varlığı yetkisiz kullanıcıya açığa çıkmaz. Otorite yine `api`'dir; UI'daki kapı yalnızca deneyimdir (§12.1).
- **Uçlar:** `api` tarafında `/api/v1/admin/sellers` öneki altında, `SecurityConfig`'te `hasRole("ADMIN")` ile korunur. Durum geçişleri komut uçlarıdır (§11.5): `POST …/{id}/approve`, `…/reject`, `…/suspend`, `…/reinstate`. Gerekçe isteyenler `{ "reason": "…" }` gövdesi alır (10–500 karakter).
- **İlk admin:** `ROLE_ADMIN` ürün yüzeyinden verilemez. İlk admin veritabanında tek bir `insert` ile atanır; bunun adımları bir how-to'da yazılır. Dev seed'i `admin@ornek.test` hesabını içerir.

**Gerekçe:**
- Ayrı bir admin uygulaması, ikinci bir dağıtılabilir birim demek; [ADR-001](../../../docs/adr/001-spring-api-and-next-web.md)'in iki birim kararı ve §3 bunu istemiyor. Mevcut oturum, BFF, tasarım sistemi ve i18n altyapısı olduğu gibi kullanılır.
- Rol verme yüzeyi en yüksek değerli saldırı hedefidir. MVP'de admin sayısı bir veya iki kişi olduğu için yüzeyi hiç açmamak en güvenli seçim.

**Reddedilen alternatifler:**
- (a) Ayrı bir admin uygulaması veya hazır bir admin paneli: yeni dağıtılabilir birim veya yeni paket (K7), §3'e aykırı.
- (b) Kararları doğrudan veritabanında vermek: rol, e-posta ve denetim izi kuralları atlanır, karar izlenemez.
- (c) Ürün yüzeyinden admin atama: bugün ihtiyaç yok ve kritik bir saldırı yüzeyi açar.

## Mevcut ADR'lerden türeyen, yeni karar içermeyen detaylar

| Detay | Kaynak |
|---|---|
| Uç adları: `/sellers/me` (oturum sahibinin kaynağı, `/accounts/me` ile aynı desen), komut uçları alt kaynak | `AGENTS.md` §11.5 |
| Hata kodları: `SELLER_ALREADY_EXISTS` (409), `SELLER_INVALID_TRANSITION` (409), `SELLER_EMAIL_NOT_VERIFIED` (422), `SELLER_NOT_APPROVED` (403), `SELLER_NOT_FOUND` (404), `DOCUMENT_TYPE_NOT_ALLOWED` ve `DOCUMENT_TOO_LARGE` (400, `fieldErrors` içinde `document` alanı) | §11.5, [ADR-031](../../../docs/adr/031-api-messages-by-accept-language.md) |
| Başkasının kaydı veya belgesi 404 döner | §11.5, [ADR-007](../../../docs/adr/007-query-level-ownership-scoping.md) |
| Liste uçları ortak `PageResponse` ile döner, sayfa boyutu sunucuda sınırlıdır (varsayılan 20, üst sınır 50, vitrinle aynı) | §11.5, storefront-catalog |
| Hız sınırı: `SELLER_APPLICATION`, hesap başına saatte 5 başvuru veya yeniden gönderim | [ADR-022](../../../docs/adr/022-in-memory-fixed-window-rate-limiting.md) |
| Karar e-postası hesabın dilinde; gönderim hatası akışı kesmez, `WARN` olarak loglanır | [ADR-032](../../../docs/adr/032-account-locale-stored-and-refreshed-on-login.md), [ADR-019](../../../docs/adr/019-email-via-smtp-provider-agnostic.md) |
| BFF Route Handler'ı ve başlık tabanlı CSRF; token tarayıcıya verilmez | [ADR-023](../../../docs/adr/023-web-bff-cookies-and-header-csrf.md) |
| Şema yalnızca Liquibase ile değişir, `--rollback` zorunlu, dev verisi `contextFilter:dev` ile | [ADR-016](../../../docs/adr/016-liquibase-for-schema-migrations.md) |

**Bu change'te alınan küçük varsayımlar** (karar düzeyinde değil; review'da değiştirilebilir):

- Üretici adı 2–160 karakter, şehir 2–80 karakter. Şehir serbest metindir; 81 il listesi teslimat bölgesi kararına (A4) kalır.
- Telefon: Türkiye numarası. Boşluk, tire ve parantez temizlenir, `0` veya `+90` öneki kabul edilir, `+90XXXXXXXXXX` (E.164) biçiminde saklanır. Ulusal numara 10 hanedir ve `0` ile başlamaz.
- `slug` onayda değil başvuruda, üretici adından üretilir. Türkçe karakterler ASCII'ye çevrilir; çakışma olursa sona `-2`, `-3` … eklenir. Sonraki change'e kadar `slug` değişmez.
- Gerekçe 10–500 karakter.

## Kişisel veri ve yetkilendirme

| Veri / uç | Kim görür | Önlem |
|---|---|---|
| Telefon | Admin | Herkese açık şemalarda yok. Satıcının kendi görünümünde (`SellerSelfView`) var. Log'a son 4 hane yazılır |
| Belge | Sahibi ve admin | Sahibine sorgu seviyesinde (`findByAccountId`), admin'e rol ile sunulur. Herkese açık adresi yok. Korumalı başlıklar (D6). İçerik ve orijinal ad loglanmaz |
| Hesap e-postası | Admin (detayda) | Log'da maskeli |
| Ret ve askı gerekçesi | Sahibi ve admin | Satıcıya gösterilir ve e-postada geçer. Admin arayüzü, gerekçenin satıcıya gösterileceğini gerekçe alanında yazar |
| `/api/v1/sellers/me*` | Oturum sahibi (rol fark etmez) | Kimlik token'dan gelir; istemci kimlik göndermez |
| `/api/v1/admin/**` | `ROLE_ADMIN` | Varsayılan deny ve açık rol eşleşmesi. Her uç için 401 ve 403 testi |

## Risks / Trade-offs

| Konu | Değerlendirme |
|---|---|
| Rolün token'a eklenmesi 15 dakikaya kadar gecikebilir | Kabul edildi. "Hesabım" ekranı satıcı kaydını her açılışta `api`'den taze okur, yani onaylı durum hemen görünür. Rol yalnızca satıcı panelini açar; bu change'te panel olmadığı için gecikmenin kullanıcıya görünen bir etkisi yok. Panel ilan change'iyle gelir |
| Antivirüs taraması yok | Belge yalnızca admin'e, `sandbox` ile gösterilir. Kabul edilen risk `00-current-state.md`'ye yazılır |
| Disk ile veritabanı arasında tutarsızlık (dosya yazıldı, transaction geri alındı) | Geri almada dosya silinir (D3). Silme de başarısız olursa yetim dosya kalır ve loglanır; zararı disk alanından ibaret |
| Belgeler birikir | Hacim MVP'de küçük. Saklama süresi hukuk kararı |
| Admin yetkisi yalnızca veritabanından verilir | Bilinçli (D7); how-to ile belgelenir |
| Seed satıcılarının hesabı yok | Bu satıcılar ürün yönetemez; yalnızca vitrini besler. İlan change'i seed'e onaylı ve hesaplı bir satıcı ekler |

## Migration Plan

1. `007-seller-onboarding.sql`:
   - `sellers` tablosuna kolonlar eklenir: `account_id bigint null` (tekil kısıt; `accounts`'a yabancı anahtar, silmede kısıtlı), `status varchar(20) not null default 'APPROVED'` (kısıt: dört değer), `phone varchar(16) null`, `status_reason varchar(500) null`, `status_changed_at timestamptz null`.
   - Ardından `alter column status drop default`.
   - Yeni tablolar: `seller_documents` ve `seller_status_changes`. İkisinin de `seller_id`'si yabancı anahtar ve indeksli; durum ve belge türü kısıtları var.
   - `--rollback` ters sırayla tabloları düşürür ve kolonları kaldırır.
   - Hesabı olan satıcılar için `phone` zorunluluğu uygulama doğrulamasındadır, çünkü seed satırlarında telefon yok.
2. `008-dev-seed-seller-onboarding.sql` (`contextFilter:dev`): iki hesap eklenir. Biri `admin@ornek.test` (`ROLE_CUSTOMER` + `ROLE_ADMIN`, e-postası doğrulanmış), diğeri e-postası doğrulanmış ama satıcı kaydı olmayan `uretici@ornek.test`. Seed başvuru içermez: belge dosyası Liquibase ile volume'a yazılamaz ve dosyası olmayan bir belge satırı tutarsız olurdu. Başvuru elle yapılır; adımlar `yerel-gelistirme.md`'de yazılır. Parola, mevcut dev hesaplarıyla aynıdır. Veri idempotenttir.
3. `docker-compose.yml`: `uploads` volume'u `api`'de `/app/uploads`'a bağlanır, `BAHCEDEN_STORAGE_DIR=/app/uploads` eklenir. `api/Dockerfile` bu dizini oluşturur ve `bahceden` kullanıcısına verir. Testler test profilinde `target/test-uploads` dizinini kullanır.
4. Uygulanmış changeset'lere dokunulmaz. `Seller` sınıfının taşınması şemayı değiştirmez.

## Open Questions

Bu change'i bloke etmeyen, ama archive'dan önce durumu yazılacak sorular:

- **Hukuk:** ÇKS belgesi veya vergi levhası yeterli mi (ADR-037)? Başvuru formunda KVKK aydınlatma metni ve açık rıza gerekiyor mu? Belge ve reddedilen başvurular ne kadar saklanır? Hukuk cevabı gelene kadar formda aydınlatma metni için yer ayrılmaz; gerekirse ayrı ve küçük bir dilim olur. Bu, `KALANLAR.md`'ye yeni bir madde olarak eklenir (A5: satıcı verisinin saklama süresi ve aydınlatma).
- Admin'e yeni başvuru bildirimi B4 kararıyla gelir.
- Onaylı satıcının profil düzenlemesi ilan change'inde mi, ayrı bir dilimde mi yapılacak? İlan change'i önerilirken karar verilir.

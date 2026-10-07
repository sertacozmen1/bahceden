# Design: add-account-and-session

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2).
> [ADR-008](../../../docs/adr/008-jwt-auth-with-opaque-refresh.md) bu dilimin çerçevesidir; aşağıdaki kararlar onun **uygulama ayrıntılarını** sabitler ve ona aykırı olamaz.

## Kararlar

### D1 · Yenileme token'ı: opak, hash'li, tek kullanımlık, aile bazlı iptal

Yenileme token'ı 256 bit rastgele bir dizedir (JWT değil). Veritabanında **SHA-256 hash'i** saklanır; düz metin yalnızca istemciye bir kez verilir.

Her kullanımda **rotasyon**: eski kayıt `used_at` ile işaretlenir, yeni token aynı **aile** (`family_id`) altında üretilir. Zaten kullanılmış bir token tekrar sunulursa bu bir sızıntı işaretidir: **o ailenin tamamı iptal edilir** ve kullanıcı yeniden giriş yapmak zorunda kalır.

Yenileme token'ının ömrü **30 gündür** (ADR-008 değeri açık bırakmıştı; 2026-10-07'de uygulama sırasında sabitlendi). Rotasyonla üretilen her yeni token yeni bir 30 günlük ömür alır. Erişim token'ının ömrü ADR-008'de 15 dakika olarak sabittir.

**Gerekçe:** ADR-008 "iptal edilebilir yenileme" dedi; bunun pratikte anlamı budur. Rotasyon olmadan ele geçirilmiş bir token ömrü boyunca kullanılabilir; aile iptali olmadan saldırgan ile kullanıcı sessizce yan yana oturum sürdürür. Hash'li saklama, veritabanı okuma yetkisi olan birinin oturum devralmasını engeller.

**Reddedilen alternatifler:** (a) Yenileme token'ını da JWT yapmak — iptal edilemez (ADR-008 bunu zaten reddetti). (b) Rotasyonsuz opak token — tek kullanımlıklık yok, sızıntı süresiz kullanılabilir. (c) Token'ı BCrypt ile hash'lemek — 256 bit entropili bir değer için gereksiz yavaş; sözlük saldırısı yüzeyi yok (bkz. D3).

### D2 · E-posta gönderimi: SMTP üzerinden, sağlayıcıdan bağımsız

E-posta `spring-boot-starter-mail` ile SMTP üzerinden gönderilir (K7 talebi `proposal.md` içinde). Sunucu bilgileri ortamdan okunur; geliştirmede `mailpit`, üretimde gerçek SMTP.

Gönderim **gönderim denemesi kaydıyla** yapılır: başarısızlık kullanıcıya hata olarak dönmez (enumeration güvenliği, D4) ama `WARN` olarak loglanır. E-posta içeriği şablon motoruyla değil düz metin + tek bağlantı olarak üretilir: bugün ihtiyaç bu kadar ve şablon motoru bütçe dışı.

**Gerekçe:** SMTP, sağlayıcı kararını (`KALANLAR` B4/B5) bu dilimden ayırır: her sağlayıcı SMTP konuşur, dolayısıyla seçim sonradan yapılabilir ve kod değişmez.

**Reddedilen alternatifler:** (a) Sağlayıcının HTTP API'si — sağlayıcı kilidi ve bu dilimi B4'e bağımlı kılar. (b) Gönderimi kuyruğa almak (asenkron) — tek sunuculu MVP için kuyruk eklemek §3 over-engineering yasağına girer; gönderim istek içinde yapılır ve hata kullanıcı akışını kesmez.

### D3 · Doğrulama token'ları: yüksek entropi + SHA-256, tek kullanımlık, 24 saat

E-posta doğrulama token'ı 256 bit rastgeledir, veritabanında **SHA-256 hash'i** saklanır, **tek kullanımlıktır** ve **24 saat** sonra geçersizdir. Tüketildiğinde `used_at` yazılır.

Token parola **değildir**: tahmin edilemez ve sözlük saldırısına açık değil. Bu yüzden BCrypt/Argon2 gibi yavaş hash kullanılmaz — SHA-256 doğru araçtır ve doğrulama sorgusu indeksli kalır.

**Gerekçe:** ADR-008 "tek kullanımlık, süreli, hash'li" dedi; hangi hash olduğu açık kalmıştı. Yavaş hash burada yalnızca maliyet üretir (her doğrulama isteğinde CPU) ve indeksli arama yapmayı zorlaştırır.

**Reddedilen alternatifler:** (a) Token'ı düz metin saklamak — veritabanı okuyan biri hesapları doğrulayabilir/devralabilir. (b) BCrypt — yavaş ve eşitlik aramasını imkânsızlaştırır (her satırı tek tek denemek gerekir). (c) İmzalı JWT token (stateless) — iptal edilemez ve tek kullanımlık yapılamaz.

### D4 · Enumeration güvenliği: yanıt hep aynı, e-posta içeriği farklı

`POST /api/v1/accounts` her durumda **202** ve aynı gövdeyi döner. Arka planda:

| Durum | Davranış |
|---|---|
| E-posta kayıtlı değil | Hesap `ACTIVE` açılır, doğrulama bağlantısı gönderilir |
| E-posta kayıtlı | Yeni hesap açılmaz; mevcut hesabın sahibine "hesabınız zaten var, giriş yapın" e-postası gönderilir |

Giriş hatalarında tek ve genel mesaj döner (`AUTH_INVALID_CREDENTIALS`): hangi alanın hatalı olduğu belirtilmez. [ADR-017](../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md) sonrası "doğrulanmamış hesap" diye bir giriş hatası **yoktur**; dolayısıyla böyle bir hata kodu da tanımlanmaz.

**Gerekçe:** Kayıt ucu, bir e-postanın sistemde olup olmadığını öğrenmek için en kolay yüzeydir. Farklı yanıt kodları veya mesajlar bu bilgiyi bedava verir.

**Reddedilen alternatifler:** (a) Kayıtlı e-postada 409 dönmek — hesap keşfi. (b) Doğrulanmamış hesapla girişte de genel hata dönmek — kullanıcı neyi düzelteceğini bilemez; bedeli yüksek, kazanç yok.

### D5 · Hız sınırlama: bellek içi sabit pencere, yeni bağımlılık yok

Kayıt, giriş, doğrulama ve yenileme uçları hız sınırlıdır: anahtar (uç + istemci IP + varsa e-posta) başına **sabit pencere** sayacı, bellekte tutulur. Sınır aşılınca **429** ve `RATE_LIMITED` döner.

Sayaç `ConcurrentHashMap` + enjekte edilen `Clock` ile yazılır; yeni paket eklenmez (Caffeine, Bucket4j, Redis **yok**).

**Gerekçe:** ADR-008 hız sınırlamayı tek örnek varsayımıyla uygulama içine koydu. Bu ölçekte bir harita yeterli; `Clock` enjekte edildiği için pencere davranışı testte sabitlenebilir.

**Reddedilen alternatifler:** (a) Bucket4j / Caffeine — bütçe dışı paket, kazanç yok. (b) Redis tabanlı dağıtık sayaç — yeni servis; yatay ölçekleme geldiğinde ADR ile gelir. (c) Hız sınırlamayı atlamak — kimlik uçlarında kaba kuvvete açık kapı.

**Bilinen sınırlama:** iki örnek çalıştırıldığında sınır örnek başına uygulanır. Bu, `00-current-state.md` sınırlamalar tablosuna yazılır.

### D6 · `web` BFF: üç Route Handler, iki çerez, başlık tabanlı CSRF savunması

Token'lar `web`'in kendi origin'inde iki `HttpOnly` + `Secure` + `SameSite=Lax` çerezde taşınır: `bahceden_at` (erişim, kısa ömür) ve `bahceden_rt` (yenileme, uzun ömür, `path=/api/auth`). Yenileme çerezi yalnızca yenileme yolunda gönderilir: XSS olmayan bir ortamda bile yüzey daraltılır.

Durum değiştiren Route Handler'lar (`login`, `logout`, `refresh`) **`Sec-Fetch-Site` ve `Origin` başlıklarını** doğrular; aynı site dışından gelen istek reddedilir. Ayrı bir CSRF token tablosu veya gizli alan **yok**.

Sunucu tarafı render erişim çerezini okuyup `api`'ye `Bearer` ile gider; `api` 401 dönerse yenileme bir kez denenir, o da başarısızsa kullanıcı `/giris`e yönlendirilir.

**Gerekçe:** ADR-008 "`web` ince bir BFF'tir" dedi ve CSRF korumasını şart koştu. `SameSite=Lax` + başlık doğrulaması, durum tutmayan ve okunması kolay bir savunma; çift gönderim token'ı bu ölçekte ek güvence sağlamadan karmaşıklık ekler.

**Reddedilen alternatifler:** (a) Çift gönderim (double submit) CSRF token'ı — her formda ek alan ve senkron yükü; `SameSite=Lax` + başlık kontrolü aynı saldırıyı kapatıyor. (b) `SameSite=Strict` — e-postadaki doğrulama bağlantısından dönen kullanıcı oturumsuz görünür, kötü deneyim. (c) Token'ları tek çerezde taşımak — yenileme token'ını her istekte göndermek gereksiz yüzey.

### D8 · İkinci faktör anahtarı hesap üzerindedir ve açık yolu da uygulanır

`accounts.two_factor_enabled boolean not null default false`. Kapalıyken giriş tek adım; açıkken iki adım: e-posta ile 6 haneli tek kullanımlık kod, 10 dakika geçerli, hash'li saklanır, denemeler hız sınırlı. Kod için ayrı mekanizma kurulmaz; D3'teki token altyapısı `login_challenges` tablosunda yeniden kullanılır.

Anahtarı değiştiren **uç veya ekran yoktur**: yalnızca veritabanından ya da dev bağlamındaki seed'den açılır.

**Kodun bağlandığı giriş girişimi (2026-10-07, uygulama sırasında netleşti):** birinci adım `202` döner ve token taşımaz. İkinci adım **e-posta + kod** gönderir; sunucu o hesabın en son açılan, süresi geçmemiş ve kullanılmamış girişimini bulur ve kodu onunla karşılaştırır. Saklanan değer `SHA-256(accountId + ":" + kod)`. Kod tek başına aranmaz: aransaydı rastgele bir kod başka bir hesabın açık girişimini tutabilirdi. Hesap kimliği hash'e girdiği için iki hesabın aynı kodu alması `code_hash` tekil kısıtına takılmaz. Aynı hesapta aynı hash'in tekrar üretilmesi (≈ 10⁻⁶) üretim anında kontrol edilir ve yeni kod çekilir.

*Reddedilen:* girişimi ayrı bir opak `challengeToken` ile tanımlamak. Bu yol yeni bir changeset gerektirir (`challenge_token_hash` kolonu ve `code_hash` tekilliğinin kaldırılması). Kullanıcı şemanın değişmemesini seçti. Kabul edilen bedel: aynı hesap için iki sekmede iki giriş başlatılırsa yalnızca son kod geçerlidir.

**Gerekçe:** [ADR-017](../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md). Açık yolun da uygulanması iki kuralın sonucudur: hiçbir yerde okunmayan bir kolon `AGENTS.md` §3 ihlalidir ("ileride lazım olur" gerekçe değildir); uygulanmamış dalı olan bir anahtar ise K6 ihlali ve bir mayındır — veritabanından açan biri girişi kırar.

**Reddedilen alternatifler:** (a) Yalnızca kolonu eklemek, akışı yazmamak — §3 ve K6 ihlali. (b) Anahtarı ortam değişkeni yapmak — hesap bazlı tercih kapısını kapatır ve "bu hesapta açık" senaryosu test edilemez. (c) TOTP (authenticator uygulaması) — e-posta altyapısı zaten kuruluyor; TOTP yeni paket (QR üretimi, gizli anahtar yönetimi) ve ayrı bir karar demek.

**Kabul edilen maliyet:** ürün yüzeyinde kapalı ama bakımı yapılan bir akış. `00-current-state.md` bunu açıkça kaydeder.

### D7 · Roller JWT iddiasında; oturum sahibi `GET /accounts/me` ile okunur

Erişim token'ı `sub` (hesap kimliği), `email` ve `roles` iddialarını taşır. Yetkilendirme kararı **yalnızca** token doğrulamasından gelen bu iddialarla verilir; `api` her istekte veritabanına gitmez.

Arayüzün ihtiyaç duyduğu "kim bağlı?" bilgisi `GET /api/v1/accounts/me` ile alınır ve bu uç veritabanından **taze** okur: rol değişikliği veya hesap askıya alınması token ömrü (15 dk) içinde ekranda görünür.

**Gerekçe:** Durumsuz yetkilendirme JWT'yi seçmenin sebebiydi; her istekte DB okumak o kazancı yok eder. Ama kullanıcıya gösterilen bilgi bayat olmamalı, bu yüzden tek bir taze okuma ucu var.

**Reddedilen alternatifler:** (a) Rolleri her istekte DB'den okumak — durumsuzluk kaybı. (b) Rolleri yalnızca `me` ucundan vermek ve token'a koymamak — her yetkilendirme kararı bir DB okuması demek. (c) Token'a kullanıcı adı/adres gibi kişisel veri koymak — JWT imzalı ama **şifreli değil**; tarayıcıya gitmese de gereksiz veri taşımak K10'a aykırı.

## Mevcut ADR'lerden türeyen, yeni karar içermeyen detaylar

| Detay | Dayanak |
|---|---|
| Erişim token'ı JWT, 15 dk; `spring-boot-starter-oauth2-resource-server` ile imzalama/doğrulama | [ADR-008](../../../docs/adr/008-jwt-auth-with-opaque-refresh.md) |
| Kayıt hesabı `ACTIVE` açar; doğrulama girişi engellemez; 2FA hesap anahtarına bağlı | [ADR-017](../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md) |
| Tek `accounts` tablosu + `account_roles`; aynı kişi hem alıcı hem satıcı | ADR-008 §3 |
| Parola BCrypt; tek `accounts` tablosu + roller | ADR-008 §3 |
| Şema yalnızca Liquibase changeset'i ile; `--rollback` zorunlu | [ADR-016](../../../docs/adr/016-liquibase-for-schema-migrations.md) |
| Ortak hata gövdesi, durum kodları, sözleşme testleri | `AGENTS.md` §11.5 |
| Sözleşme DTO'lardan üretilir; tazelik testi | [ADR-009](../../../docs/adr/009-contract-generated-from-spring.md), [ADR-015](../../../docs/adr/015-contract-freshness-enforced-by-test.md) |
| Dört durum, mobil önce, tek metin modülü, CSS Modules | `AGENTS.md` §12.4, §12.5 |
| Kişisel veri maskeli loglanır (e-posta `a***@example.com`) | `AGENTS.md` K10 |

## Risks / Trade-offs

| Konu | Değerlendirme |
|---|---|
| Hız sınırlamanın tek örneğe bağlı olması | D5'te gerekçeli; bilinen sınırlama olarak kaydedilir |
| E-posta teslim edilemezse kayıt tamamlanamaz | Zorunlu doğrulama ADR-008 kararı; `mailpit` ile geliştirmede görünür, üretim izlemesi B5'e bağlı |
| Parola sıfırlama yok | Bilinçli non-goal; token altyapısı bu dilimde kurulduğu için sonraki dilim küçük olacak. [ADR-017](../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md) sonrası kritik değil: doğrulama e-postası ulaşmayan kullanıcı kilitlenmiyor |
| JWT iddialarının bayatlığı (en fazla 15 dk) | D7'de kabul edildi; askıya alma ihtiyacı doğarsa kara liste ayrı bir karar |
| Güvenlik kurallarının testle korunması | Her kural için `critical` test zorunlu; `tasks.md` bunları ayrı görev olarak sayıyor |

## Migration Plan

1. Liquibase `003-create-account.sql`: `accounts` (`email_verified`, `two_factor_enabled` dahil), `account_roles`, `email_verification_tokens`, `refresh_tokens`, `login_challenges`; `accounts.email` tekil indeks, token ve kod hash tekil indeksleri, `expires_at` indeksleri. `--rollback` satırları tabloları düşürür.
2. Geliştirme verisi: `004-dev-seed-account.sql`, `contextFilter:dev` — iki sahte hesap: ikinci faktörü **kapalı** bir alıcı (olağan akış) ve ikinci faktörü **açık** bir alıcı (anahtarın açık yolunu elle denemek için). Parola hash'leri changeset'e sabit yazılır ve yalnızca dev bağlamında çalıştığı yorumda belirtilir.
3. Geri alma: üretim veritabanı henüz yok; yine de her changeset geri alınabilir yazılır.
4. Mevcut `catalog` şeması etkilenmez: `products.seller_id` ile `accounts` arasında ilişki **kurulmaz** (satıcı-hesap bağı `KALANLAR` A3'e bağlı, [ADR-013](../../../docs/adr/013-minimal-seller-table-ahead-of-onboarding.md)).

## Open Questions

Bu dilimi bloke **etmeyen**, sonraki dilimlere bırakılan sorular:

- Hesap askıya alma / kapatma durumu (`SUSPENDED`) gerekecek mi? Bugün tek değer `ACTIVE`; enum olarak tutulmasının sebebi ikinci durumun şema göçü gerektirmemesi ([ADR-010](../../../docs/adr/010-product-status-as-enum.md) deseni).
- Erişim token'ı ömrü 15 dk doğru değer mi? Ölçüm yapılmadan değiştirilmeyecek; ADR-008'de sabit.
- `web` tarafında oturum bilgisi her sayfada `me` çağrısıyla mı alınacak, yoksa düzende bir kez mi? İlk uygulamada düzende bir kez; ölçüm sonrası gözden geçirilir.

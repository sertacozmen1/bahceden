# Tasks: add-account-and-session

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir; dış/insan işi açık kalır ve notlanır (K6).
> **Bağımlılık onayı alındı (2026-10-06):** `spring-boot-starter-mail` bütçeye eklenir (K7; ADR ile kaydedilir).
> **Kapsam güncellendi:** e-posta doğrulaması girişi **engellemez**; e-postanın kimlik akışına girmesi hesap bazlı `two_factor_enabled` anahtarına bağlıdır ve anahtar varsayılan kapalıdır ([ADR-017](../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md)).
> Faz sonlarında `AGENTS.md` §7 doğrulama komutları koşulur ve çıktılar raporlanır.

## Faz 1 · Hesap şeması, e-posta altyapısı ve kayıt

- [x] 1.1 `003-create-account.sql`: `accounts` (`email` tekil, `password_hash`, `status` enum metni + `CHECK`, **`email_verified boolean not null default false`**, **`two_factor_enabled boolean not null default false`**, zaman damgaları), `account_roles` (`account_id` + `role`, bileşik tekil), `email_verification_tokens` (`token_hash` tekil, `account_id`, `expires_at`, `used_at`), `refresh_tokens` (`token_hash` tekil, `account_id`, `family_id`, `expires_at`, `used_at`, `revoked_at`), `login_challenges` (`code_hash` tekil, `account_id`, `expires_at`, `used_at`, `attempt_count`); `expires_at` indeksleri. `--rollback` satırları.
- [x] 1.2 `account/domain/`: `Account` (`emailVerified`, `twoFactorEnabled` dahil), `AccountRole`, `AccountStatus` (bugün tek değer: `ACTIVE`), `EmailVerificationToken`. **Sapma (S12):** `RefreshToken` ve `LoginChallenge` entity'leri kendi fazlarında yazılır; tabloları `003` ile oluştu ama kullanılmayan entity ölü kod olurdu (K6). `ddl-auto: validate` eşlenmemiş tabloyu sorun saymaz.
- [x] 1.3 `common/security/`: `PasswordHasher` (BCrypt, Spring Security `PasswordEncoder` üzerinden) ve `SecureTokens` (256 bit üretim + SHA-256 hash; D1/D3 tek yardımcıda).
- [x] 1.4 `common/mail/`: `MailSender` sarmalayıcı — düz metin + tek bağlantı; gönderim hatası `WARN` loglanır ve akışı kesmez (D2). Ortam değişkenleri `api/.env.example`'a eklenir; `docker-compose.yml` `api` servisine `mailpit` SMTP ayarları.
- [x] 1.5 `AccountService.register(...)`: D4 tablosundaki iki durumu uygular; hesap **`ACTIVE`** açılır (ADR-017), `ROLE_CUSTOMER` verilir, doğrulama token'ı üretilir ve e-posta gönderilir.
- [x] 1.6 `POST /api/v1/accounts` ucu + DTO'lar (`RegisterRequest` Bean Validation ile: e-posta biçimi, parola en az 10 karakter) + `@Schema` açıklamaları. Uç `permitAll`, açıkça işaretli.
- [x] 1.7 **Test (`critical`):** kayıt iki durumda da **202 ve aynı gövde** döner (enumeration, D4); var olan e-postayla kayıt ikinci hesap açmaz; hesap `ACTIVE` + `email_verified=false` + ikinci faktör kapalı açılır (ADR-017 değişmezi); parola düz metin hiçbir yere yazılmaz ve hata gövdesine sızmaz. **Sapma (S13):** "doğrulama yapılmadan giriş çalışır" iddiası Faz 2'ye taşındı — giriş ucu henüz yok, test edilemezdi.
- [x] 1.8 **Test (`integration`):** bellek içi gönderici ile doğrulama bağlantısının üretildiği ve token'ın **hash'li** saklandığı doğrulanır (düz token DB'de yok); **gönderim hatası kaydı başarısız kılmaz** (ADR-017 §4).
- [x] 1.9 **Doğrulama:** `./mvnw -B verify` temiz; `docker compose up` ile kayıt isteği atılıp e-posta `mailpit` arayüzünde (http://localhost:8025) görülür.

> **Faz 1 tamamlandı (2026-10-07).** 31 `api` testi yeşil (11'i kayıt). Uçtan uca doğrulandı:
> `003-create-account` uygulandı; kayıt 202 + standart gövde; aynı e-postayla ikinci kayıt
> **birebir aynı** yanıtı döndü ve "hesabınız zaten var" e-postası gitti; geçersiz parola ortak
> hata gövdesiyle reddedildi; `mailpit` iki e-postayı aldı; veritabanında hesap
> `ACTIVE / email_verified=f / two_factor_enabled=f` ve parola `$2a$12$` hash'i olarak duruyor.

## Faz 2 · E-posta doğrulama (engellemeyen) ve giriş

- [x] 2.1 `AccountService.confirmVerification(token)`: hash ile arar, süre ve tek kullanımlık kontrolü, `email_verified` işaretler ve token'ı tüketir (D3). Girişe etkisi **yoktur** (ADR-017).
- [x] 2.2 `POST /api/v1/accounts/verifications` ucu + DTO; geçersiz/süresi dolmuş/kullanılmış token için `AUTH_TOKEN_INVALID`.
- [x] 2.3 `common/security/`: JWT üretimi ve doğrulaması (`JwtEncoder`/`JwtDecoder`, imza anahtarı ortamdan, açılışta fail-fast). `SecurityConfig` resource server olarak yapılandırılır; `/api/v1/accounts/me` korunur, kimlik uçları `permitAll`.
- [x] 2.4 `SessionService.login(...)`: parola doğrulama; `two_factor_enabled` **kapalıysa** erişim JWT'si (15 dk, `sub`/`email`/`roles` iddiaları — D7) + yenileme token'ı (yeni `family_id`); **açıksa** token dönmez, `login_challenges` kaydı ve 6 haneli kod üretilir, e-posta gönderilir (D8).
- [x] 2.5 `POST /api/v1/sessions` ucu + DTO'lar.
- [x] 2.6 `GET /api/v1/accounts/me` ucu: veritabanından **taze** okur (D7); e-posta, roller ve `emailVerified` döner; kimliksiz istek 401.
- [x] 2.7 `ApiErrorCode` genişletilir: `AUTH_INVALID_CREDENTIALS`, `AUTH_TOKEN_INVALID`, `AUTH_CODE_INVALID`, `RATE_LIMITED`. (`AUTH_EMAIL_NOT_VERIFIED` **tanımlanmaz**: ADR-017 sonrası böyle bir giriş hatası yok.) **Sapma (S14):** `AUTH_CODE_INVALID` ve `RATE_LIMITED` Faz 3'te, onları üreten kodla birlikte eklenir (`ApiErrorCode` yalnızca gerçekten üretilen kodları tutar, K6). **Sapma (S15):** planda olmayan `AUTH_REQUIRED` (401) eklendi: korunan uca token'sız veya geçersiz token'la gelen istek için. Bu kod olmadan 401 gövdesiz dönerdi ve §11.5 tek hata gövdesi kuralı kırılırdı. Durum kodları artık koda bağlı (`ApiErrorCode#status`): `AUTH_INVALID_CREDENTIALS` 401, `AUTH_TOKEN_INVALID` 400, `AUTH_REQUIRED` 401.
- [x] 2.8 **Test (`critical`):** hatalı parola ile hatalı e-posta **aynı** kodu ve mesajı döner; doğrulanmamış hesap **giriş yapabilir** (ADR-017); doğrulama token'ı **iki kez** kullanılamaz; süresi dolmuş token reddedilir; `me` kimliksiz 401, geçerli token ile doğru rolleri ve `emailVerified` değerini döner.
- [x] 2.9 **Doğrulama:** `./mvnw -B verify` temiz; uçtan uca `curl` ile kayıt → giriş → `me` akışı (e-postaya hiç dokunmadan) ve ayrıca `mailpit`ten token → doğrulama → `me` içinde `emailVerified: true`.

- [x] 2.10 `POST /api/v1/accounts/me/verification-requests` (D9): oturum ister, gövde almaz, `202`. Doğrulanmamış hesaba yeni token üretilip gönderilir; doğrulanmış hesapta e-posta gönderilmez. `AUTH_TOKEN_INVALID` mesajı kullanıcıyı bu uca yönlendirir. **Eklenme sebebi:** spec'teki "süresi dolmuş token" senaryosu yeni bağlantı istemeyi vaat ediyordu ama bunu yapan bir uç yoktu (2026-10-07, kullanıcı onayıyla).
- [x] 2.11 **Test (`critical`):** doğrulanmamış hesap yeni bağlantı alır ve bu bağlantı hesabı doğrular; doğrulanmış hesapta e-posta gönderilmez; oturumsuz istek 401.

> **Faz 2 tamamlandı (2026-10-07).** 55 `api` testi yeşil (24'ü yeni: doğrulama 5, giriş 8, `me` 8, imza anahtarı 3).
> Uçtan uca `docker compose` doğrulandı: kayıt 202 → e-postaya dokunmadan giriş 200 → `me` `emailVerified:false`;
> hatalı parola ile olmayan e-posta birebir aynı 401; token'sız `me` 401 `AUTH_REQUIRED`; `mailpit`ten token → 204 →
> `me` `emailVerified:true`; aynı token ikinci kez 400 `AUTH_TOKEN_INVALID`. İkinci faktörü veritabanından açılan
> hesapta giriş 202 döndü, kod e-postası `mailpit`e düştü, açık girişim kaydı oluştu (anahtar sonra kapatıldı).
> **Faz 3 için not:** ikinci adım `e-posta + kod` alır, hesabın en son açık girişimine karşı doğrular (design.md D8, 2026-10-07 eki).
> **Yan etki:** `denyAll` kapsamındaki bilinmeyen bir uca kimliksiz istek artık 403 değil 401 + `AUTH_REQUIRED` alır
> (Spring, kimliksiz isteği yetki reddinden önce kimlik girişine yönlendirir). Uç yine kapalıdır.

## Faz 3 · İkinci faktör, yenileme, çıkış ve hız sınırlama

- [ ] 3.0 `SessionService.completeChallenge(...)` + `POST /api/v1/sessions/challenge`: kodu hash ile arar, süre / tek kullanımlık / deneme sayısı kontrolü, başarılıysa token'ları verir ve kodu tüketir (D8). Anahtar kapalı olduğu sürece ürün yüzeyinde kullanılmaz ama uygulanır ve testlenir (K6).
- [ ] 3.1 `SessionService.refresh(...)`: hash ile arar; süre/iptal/kullanım kontrolü; **rotasyon** (eski kayıt `used_at`, yeni token aynı ailede); **tekrar kullanım tespitinde ailenin tamamı iptal** (D1).
- [ ] 3.2 `POST /api/v1/sessions/refresh` ve `DELETE /api/v1/sessions/current` uçları (çıkış ailenin tamamını iptal eder).
- [ ] 3.3 `common/ratelimit/`: sabit pencere sayacı (`ConcurrentHashMap` + enjekte edilen `Clock`, D5); kayıt / giriş / doğrulama / yenileme / doğrulama bağlantısı talebi uçlarına uygulanır; aşımda 429 + `RATE_LIMITED`.
- [ ] 3.4 **Test (`critical`):** yenileme token'ı tek kullanımlıktır; **tekrar kullanım ailenin tamamını iptal eder** ve sonraki yenileme reddedilir; çıkış sonrası yenileme reddedilir; iptal edilmiş aileyle `me` çağrısı erişim token'ı süresi dolunca başarısız olur.
- [ ] 3.5 **Test (`critical`):** hız sınırı — sabit `Clock` ile pencere içinde N+1. istek 429 döner, pencere kayınca yeniden açılır.
- [ ] 3.5b **Test (`critical`, D8):** ikinci faktörü **kapalı** hesap tek adımda token alır ve e-posta gönderilmez; **açık** hesapta birinci adım token döndürmez ve kod gönderilir; geçerli kod token verir; kod **iki kez** kullanılamaz; süresi dolmuş kod reddedilir; hatalı deneme sayısı sınırı aşılınca 429.
- [ ] 3.6 **Doğrulama:** `./mvnw -B verify` temiz; sözleşme yeniden üretilir (`-Dopenapi.write=true`) ve diff commit'e dahil edilir.

## Faz 4 · `web` BFF ve ekranlar

- [ ] 4.1 `npm run gen:api` ile tipler yeniden üretilir; `apiClient` POST desteği ve §11.5 hata gövdesi çözümü genişletilir (ikinci sarmalayıcı yazılmaz — K9).
- [ ] 4.2 `web/src/lib/session.ts`: çerez okuma/yazma/silme (`bahceden_at`, `bahceden_rt`; `HttpOnly` + `Secure` + `SameSite=Lax`, yenileme çerezi `path=/api/auth` — D6).
- [ ] 4.3 Route Handler'lar: `/api/auth/login`, `/api/auth/logout`, `/api/auth/refresh`. Her biri `Sec-Fetch-Site` / `Origin` doğrular (D6); token'ı istemciye **döndürmez**, yalnızca çereze yazar.
- [ ] 4.4 Sunucu tarafı veri çekme: erişim çerezi `Bearer` olarak iletilir; 401 alınırsa **bir kez** yenileme denenir, başarısızsa `/giris`e yönlendirilir.
- [ ] 4.5 Ekranlar: `/kayit`, `/giris` (sunucu ikinci adım beklediğini bildirirse aynı ekranda kod alanı açılır), `/dogrulama` (e-postadaki bağlantının indiği sayfa; token'ı sunucu tarafında tüketir), `/hesabim` (korunan; `me` verisini ve e-posta doğrulama durumunu gösterir). Her biri için **dört durum** (§12.5).
- [ ] 4.6 `AppShell` oturum durumunu gösterir: girişliyse maskeli e-posta + çıkış, değilse giriş bağlantısı. Oturum bilgisi düzende **bir kez** okunur (design.md Open Questions).
- [ ] 4.7 Metinler `texts.ts` içine; form hataları sunucudan gelen `fieldErrors` ile alanlara bağlanır (§12.3).
- [ ] 4.8 **Test (web):** kayıt formu (doğrulama hatası, sunucu hatası eşlemesi, başarı); giriş formu (hatalı kimlik → genel mesaj; ikinci adım beklenince kod alanı açılır); `/hesabim` yetkisiz durumu. Ağ mock'lu (§12.6).
- [ ] 4.9 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz; `docker compose up` ile tarayıcıdan kayıt → giriş → `/hesabim` → çıkış akışı (e-posta gerekmeden) ve ayrıca dev seed'deki **ikinci faktörü açık** hesapla kod adımı elle denenir.

## Faz 5 · Doküman ve kapanış

- [ ] 5.1 ADR-018…026: D1–D9 kararları (`docs/adr/` + `index.md` + nav). [ADR-017](../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md) ve `spring-boot-starter-mail` bütçe kaydı bu dilim başlarken yazıldı.
- [ ] 5.2 `docs/features/hesap-ve-oturum.md` (yeni feature dosyası) + `features/index.md` tablosu + nav; `features/index.md` "Planlanan" tablosundan satır düşer.
- [ ] 5.3 `docs/reference/01-domain-model.md`: dört yeni tablo + ER diyagramı; `products.seller_id` ↔ `accounts` ilişkisinin **bilinçli yokluğu** not edilir.
- [ ] 5.4 `docs/reference/02-api-reference.md`: sekiz yeni uç, yeni hata kodları, çerez/BFF akışı.
- [ ] 5.5 `docs/how-to/korunan-uc-ekleme.md`: yetkilendirme kuralı yazma, rol kontrolü, sahiplik kapsamı, dört zorunlu test; `how-to/index.md` + nav.
- [ ] 5.6 `docs/reference/00-current-state.md`: katman durumları, hız sınırlama tek örnek sınırlaması (D5), parola sıfırlama boşluğu, **doğrulanmamış e-postayla hesap açılabilmesi** (ADR-017 kabul edilen borcu) ve ürün yüzeyinde kapalı 2FA akışı.
- [ ] 5.7 `docs/features/_genel.md` *Tarihçe*: tek satır (K1).
- [ ] 5.8 `./docs/.tools/serve.sh --build` strict temiz.
- [ ] 5.9 Delta spec ana spec'e sync edilir (`openspec/specs/account-identity/spec.md`).
- [ ] 5.10 CI 4/4 yeşil. **Doğrulama:** run numarası rapora.
- [ ] 5.11 Rapor `AGENTS.md` §14 formatında; archive **kullanıcı onayı** ister (§9).

## Sonraki dilim adayları

- **Parola sıfırlama** (bu dilimin token altyapısı üzerine oturur; en küçük sonraki adım).
- Satıcı olma akışı ve `ROLE_SELLER` ataması (`KALANLAR` A3'e bağlı).
- Satıcı ürün yönetimi: kimlik + sahiplik artık mevcut ([ADR-007](../../../docs/adr/007-query-level-ownership-scoping.md) uygulanabilir).

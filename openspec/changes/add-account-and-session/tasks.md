# Tasks: add-account-and-session

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir; dış/insan işi açık kalır ve notlanır (K6).
> **Faz 1 bloke:** `spring-boot-starter-mail` bütçe talebi onaylanmadan e-posta gönderen görevler başlamaz (K7, `proposal.md`).
> Faz sonlarında `AGENTS.md` §7 doğrulama komutları koşulur ve çıktılar raporlanır.

## Faz 1 · Hesap şeması ve kayıt (e-posta ile)

- [ ] 1.1 `003-create-account.sql`: `accounts` (`email` tekil, `password_hash`, `status` enum metni + `CHECK`, zaman damgaları), `account_roles` (`account_id` + `role`, bileşik tekil), `email_verification_tokens` (`token_hash` tekil, `account_id`, `expires_at`, `used_at`), `refresh_tokens` (`token_hash` tekil, `account_id`, `family_id`, `expires_at`, `used_at`, `revoked_at`); `expires_at` indeksleri. `--rollback` satırları.
- [ ] 1.2 `account/domain/`: `Account`, `AccountRole`, `AccountStatus` (`PENDING_VERIFICATION`, `ACTIVE`), `EmailVerificationToken`, `RefreshToken` entity'leri.
- [ ] 1.3 `common/security/`: `PasswordHasher` (BCrypt, Spring Security `PasswordEncoder` üzerinden) ve `SecureTokens` (256 bit üretim + SHA-256 hash; D1/D3 tek yardımcıda).
- [ ] 1.4 `common/mail/`: `MailSender` sarmalayıcı — düz metin + tek bağlantı; gönderim hatası `WARN` loglanır ve akışı kesmez (D2). Ortam değişkenleri `api/.env.example`'a eklenir; `docker-compose.yml` `api` servisine `mailpit` SMTP ayarları.
- [ ] 1.5 `AccountService.register(...)`: D4 tablosundaki üç durumu uygular; hesap `PENDING_VERIFICATION` açılır, `ROLE_CUSTOMER` verilir, token üretilir ve e-posta gönderilir.
- [ ] 1.6 `POST /api/v1/accounts` ucu + DTO'lar (`RegisterRequest` Bean Validation ile: e-posta biçimi, parola en az 10 karakter) + `@Schema` açıklamaları. Uç `permitAll`, açıkça işaretli.
- [ ] 1.7 **Test (`critical`):** kayıt üç durumda da **202 ve aynı gövde** döner (enumeration, D4); doğrulanmamış hesaba ikinci kayıt yeni hesap açmaz, token yeniler; parola düz metin hiçbir yere yazılmaz; e-posta log'a maskeli düşer (K10).
- [ ] 1.8 **Test (`integration`):** `mailpit` yerine bellek içi gönderici ile doğrulama bağlantısının üretildiği ve token'ın **hash'li** saklandığı doğrulanır (düz token DB'de yok).
- [ ] 1.9 **Doğrulama:** `./mvnw -B verify` temiz; `docker compose up` ile kayıt isteği atılıp e-posta `mailpit` arayüzünde (http://localhost:8025) görülür.

## Faz 2 · E-posta doğrulama ve giriş

- [ ] 2.1 `AccountService.confirmVerification(token)`: hash ile arar, süre ve tek kullanımlık kontrolü, hesabı `ACTIVE` yapar, token'ı tüketir (D3).
- [ ] 2.2 `POST /api/v1/accounts/verifications` ucu + DTO; geçersiz/süresi dolmuş/kullanılmış token için `AUTH_TOKEN_INVALID`.
- [ ] 2.3 `common/security/`: JWT üretimi ve doğrulaması (`JwtEncoder`/`JwtDecoder`, imza anahtarı ortamdan, açılışta fail-fast). `SecurityConfig` resource server olarak yapılandırılır; `/api/v1/accounts/me` korunur, kimlik uçları `permitAll`.
- [ ] 2.4 `SessionService.login(...)`: parola doğrulama, `PENDING_VERIFICATION` ise `AUTH_EMAIL_NOT_VERIFIED`, başarılıysa erişim JWT'si (15 dk, `sub`/`email`/`roles` iddiaları — D7) + yenileme token'ı (yeni `family_id`).
- [ ] 2.5 `POST /api/v1/sessions` ucu + DTO'lar.
- [ ] 2.6 `GET /api/v1/accounts/me` ucu: veritabanından **taze** okur (D7); kimliksiz istek 401.
- [ ] 2.7 `ApiErrorCode` genişletilir: `AUTH_INVALID_CREDENTIALS`, `AUTH_EMAIL_NOT_VERIFIED`, `AUTH_TOKEN_INVALID`, `RATE_LIMITED`.
- [ ] 2.8 **Test (`critical`):** doğrulanmamış hesapla giriş reddedilir; hatalı parola ile hatalı e-posta **aynı** kodu ve mesajı döner; doğrulama token'ı **iki kez** kullanılamaz; süresi dolmuş token reddedilir; `me` kimliksiz 401, geçerli token ile doğru rolleri döner.
- [ ] 2.9 **Doğrulama:** `./mvnw -B verify` temiz; uçtan uca `curl` ile kayıt → `mailpit`ten token → doğrulama → giriş → `me` akışı.

## Faz 3 · Yenileme, çıkış ve hız sınırlama

- [ ] 3.1 `SessionService.refresh(...)`: hash ile arar; süre/iptal/kullanım kontrolü; **rotasyon** (eski kayıt `used_at`, yeni token aynı ailede); **tekrar kullanım tespitinde ailenin tamamı iptal** (D1).
- [ ] 3.2 `POST /api/v1/sessions/refresh` ve `DELETE /api/v1/sessions/current` uçları (çıkış ailenin tamamını iptal eder).
- [ ] 3.3 `common/ratelimit/`: sabit pencere sayacı (`ConcurrentHashMap` + enjekte edilen `Clock`, D5); kayıt / giriş / doğrulama / yenileme uçlarına uygulanır; aşımda 429 + `RATE_LIMITED`.
- [ ] 3.4 **Test (`critical`):** yenileme token'ı tek kullanımlıktır; **tekrar kullanım ailenin tamamını iptal eder** ve sonraki yenileme reddedilir; çıkış sonrası yenileme reddedilir; iptal edilmiş aileyle `me` çağrısı erişim token'ı süresi dolunca başarısız olur.
- [ ] 3.5 **Test (`critical`):** hız sınırı — sabit `Clock` ile pencere içinde N+1. istek 429 döner, pencere kayınca yeniden açılır.
- [ ] 3.6 **Doğrulama:** `./mvnw -B verify` temiz; sözleşme yeniden üretilir (`-Dopenapi.write=true`) ve diff commit'e dahil edilir.

## Faz 4 · `web` BFF ve ekranlar

- [ ] 4.1 `npm run gen:api` ile tipler yeniden üretilir; `apiClient` POST desteği ve §11.5 hata gövdesi çözümü genişletilir (ikinci sarmalayıcı yazılmaz — K9).
- [ ] 4.2 `web/src/lib/session.ts`: çerez okuma/yazma/silme (`bahceden_at`, `bahceden_rt`; `HttpOnly` + `Secure` + `SameSite=Lax`, yenileme çerezi `path=/api/auth` — D6).
- [ ] 4.3 Route Handler'lar: `/api/auth/login`, `/api/auth/logout`, `/api/auth/refresh`. Her biri `Sec-Fetch-Site` / `Origin` doğrular (D6); token'ı istemciye **döndürmez**, yalnızca çereze yazar.
- [ ] 4.4 Sunucu tarafı veri çekme: erişim çerezi `Bearer` olarak iletilir; 401 alınırsa **bir kez** yenileme denenir, başarısızsa `/giris`e yönlendirilir.
- [ ] 4.5 Ekranlar: `/kayit`, `/giris`, `/dogrulama` (e-postadaki bağlantının indiği sayfa; token'ı sunucu tarafında tüketir), `/hesabim` (korunan; `me` verisini gösterir). Her biri için **dört durum** (§12.5).
- [ ] 4.6 `AppShell` oturum durumunu gösterir: girişliyse maskeli e-posta + çıkış, değilse giriş bağlantısı. Oturum bilgisi düzende **bir kez** okunur (design.md Open Questions).
- [ ] 4.7 Metinler `texts.ts` içine; form hataları sunucudan gelen `fieldErrors` ile alanlara bağlanır (§12.3).
- [ ] 4.8 **Test (web):** kayıt formu (doğrulama hatası, sunucu hatası eşlemesi, başarı); giriş formu (hatalı kimlik → genel mesaj, doğrulanmamış hesap → yönlendirici mesaj); `/hesabim` yetkisiz durumu. Ağ mock'lu (§12.6).
- [ ] 4.9 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz; `docker compose up` ile tarayıcıdan kayıt → `mailpit` → doğrulama → giriş → `/hesabim` → çıkış akışı elle denenir.

## Faz 5 · Doküman ve kapanış

- [ ] 5.1 ADR-017…023: D1–D7 kararları (`docs/adr/` + `index.md` + nav).
- [ ] 5.2 `docs/features/hesap-ve-oturum.md` (yeni feature dosyası) + `features/index.md` tablosu + nav; `features/index.md` "Planlanan" tablosundan satır düşer.
- [ ] 5.3 `docs/reference/01-domain-model.md`: dört yeni tablo + ER diyagramı; `products.seller_id` ↔ `accounts` ilişkisinin **bilinçli yokluğu** not edilir.
- [ ] 5.4 `docs/reference/02-api-reference.md`: yedi yeni uç, yeni hata kodları, çerez/BFF akışı.
- [ ] 5.5 `docs/how-to/korunan-uc-ekleme.md`: yetkilendirme kuralı yazma, rol kontrolü, sahiplik kapsamı, dört zorunlu test; `how-to/index.md` + nav.
- [ ] 5.6 `docs/reference/00-current-state.md`: katman durumları, hız sınırlama tek örnek sınırlaması (D5), parola sıfırlama boşluğu.
- [ ] 5.7 `docs/features/_genel.md` *Tarihçe*: tek satır (K1).
- [ ] 5.8 `./docs/.tools/serve.sh --build` strict temiz.
- [ ] 5.9 Delta spec ana spec'e sync edilir (`openspec/specs/account-identity/spec.md`).
- [ ] 5.10 CI 4/4 yeşil. **Doğrulama:** run numarası rapora.
- [ ] 5.11 Rapor `AGENTS.md` §14 formatında; archive **kullanıcı onayı** ister (§9).

## Sonraki dilim adayları

- **Parola sıfırlama** (bu dilimin token altyapısı üzerine oturur; en küçük sonraki adım).
- Satıcı olma akışı ve `ROLE_SELLER` ataması (`KALANLAR` A3'e bağlı).
- Satıcı ürün yönetimi: kimlik + sahiplik artık mevcut ([ADR-007](../../../docs/adr/007-query-level-ownership-scoping.md) uygulanabilir).

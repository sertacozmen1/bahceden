# API Referansı

**Son doğrulama:** 2026-10-08 · Kaynak: `shared/contracts/openapi.yaml`

> Sözleşme `api/` içindeki DTO ve anotasyonlardan **üretilir** ([ADR-009](../adr/009-contract-generated-from-spring.md)) ve tazeliği bir testle zorunludur ([ADR-015](../adr/015-contract-freshness-enforced-by-test.md)). Bu sayfa sözleşmeyi okunur hale getirir; çelişki halinde **sözleşme dosyası** esastır.

---

## Ortak kurallar

### Sayfalı liste yanıtı

Tüm liste uçları aynı şekli kullanır (`AGENTS.md` §11.5):

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

`size` alanı **uygulanan** sayfa boyutunu bildirir: istek üst sınırı aşarsa sunucu değeri sınıra çeker ([ADR-012](../adr/012-page-size-capped-on-server.md)).

Sözleşmede her öğe tipi için ayrı bir şema üretilir: `PageResponseProductSummary`, `PageResponseAdminSellerSummary`, `PageResponseSellerListingView`, `PageResponseAdminProductSummary`. JSON şekli hepsinde aynıdır. Eskiden tek bir `PageResponse` şeması vardı; ikinci liste ucu gelince ikisi aynı şemaya düşüyordu, bu yüzden 2026-10-08'de ayrıldı ([ADR-046](../adr/046-admin-surface-in-web-first-admin-by-sql.md) Trade-off).

### Hata gövdesi

Tüm hatalar aynı şekli kullanır:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Gönderilen değerler geçersiz.",
  "fieldErrors": [{ "field": "page", "message": "Sayfa numarası negatif olamaz" }]
}
```

- `code` İngilizce ve sabittir; istemci buna göre dallanır. Her kodun durum kodu sabittir (aynı kod her uçta aynı durumla döner):

| Kod | Durum | Ne zaman |
|---|---|---|
| `VALIDATION_FAILED` | 400 | İstek gövdesi veya parametresi doğrulamadan geçmedi; `fieldErrors` dolu |
| `AUTH_INVALID_CREDENTIALS` | 401 | E-posta veya parola hatalı; hangisi olduğu söylenmez ([ADR-021](../adr/021-identity-responses-do-not-reveal-accounts.md)) |
| `AUTH_TOKEN_INVALID` | 400 | Doğrulama veya yenileme token'ı geçersiz, kullanılmış, iptal edilmiş ya da süresi dolmuş |
| `AUTH_CODE_INVALID` | 401 | İkinci faktör kodu hatalı, kullanılmış, süresi dolmuş ya da açık girişim yok |
| `AUTH_REQUIRED` | 401 | Korunan uca erişim token'ı olmadan, geçersiz ya da süresi dolmuş token'la gelindi (`WWW-Authenticate: Bearer`) |
| `RATE_LIMITED` | 429 | Hız sınırı aşıldı ya da girişim başına 5 hatalı kod denendi ([ADR-022](../adr/022-in-memory-fixed-window-rate-limiting.md)) |
| `AUTH_FORBIDDEN` | 403 | Kimlik geçerli ama rol yetmiyor (örn. admin olmayan hesabın admin uçları) |
| `SELLER_NOT_FOUND` | 404 | Oturum sahibinin satıcı kaydı yok; ya da istenen kayıt veya belge yok |
| `SELLER_ALREADY_EXISTS` | 409 | Hesabın zaten bir satıcı kaydı var |
| `SELLER_INVALID_TRANSITION` | 409 | Kaydın şimdiki durumundan bu geçiş yapılamaz |
| `SELLER_EMAIL_NOT_VERIFIED` | 422 | Başvuru için e-posta doğrulaması gerekir ([ADR-043](../adr/043-seller-application-requires-verified-email.md)) |
| `SELLER_NOT_APPROVED` | 403 | Satıcıya özgü işlem onaylı satıcı ister ([ADR-044](../adr/044-seller-role-follows-status-with-query-gate.md)); bugün bunu çağıran uç yok |
| `PRODUCT_NOT_FOUND` | 404 | İlan yok, başkasına ait ya da vitrinde görünmüyor; fotoğraf anahtarı kayıtlı değil |
| `PRODUCT_INVALID_TRANSITION` | 409 | İlanın şimdiki durumundan bu geçiş yapılamaz ([ADR-047](../adr/047-listing-states-removed-and-named-transitions.md)) |
| `PRODUCT_PHOTO_REQUIRED` | 409 | Yayına alma / yeniden açma fotoğraf ister; yayındaki ilanın son fotoğrafı silinemez |
| `PRODUCT_PHOTO_LIMIT_REACHED` | 409 | İlan başına 5 fotoğraf sınırı doldu |
| `INTERNAL_ERROR` | 500 | Beklenmeyen hata; ayrıntı yalnızca sunucu log'unda |

- `message` kullanıcıya gösterilebilir ve **isteğin dilindedir**: `Accept-Language` başlığına göre `tr` ya da `en`; başlık yoksa veya yalnızca desteklenmeyen dil bildiriyorsa Türkçe. Alan hataları (`fieldErrors[].message`) ve bilgilendirme yanıtlarındaki `message` alanları da aynı kurala uyar. `code` dilden bağımsızdır ([ADR-031](../adr/031-api-messages-by-accept-language.md)).
- `web` BFF her çağrıda sayfanın seçili dilini `Accept-Language` olarak iletir ([ADR-030](../adr/030-locale-from-cookie-then-header.md)). Hesaba giden e-postalar ise isteğin değil hesabın dilindedir ([ADR-032](../adr/032-account-locale-stored-and-refreshed-on-login.md)); oturum açıkken dil menüsünden yapılan seçim hesabın dilini de günceller ([ADR-099](../adr/099-locale-menu-syncs-account-best-effort.md)).
- `fieldErrors` her zaman vardır; alan hatası yoksa boş dizidir.
- Stack trace, SQL ve iç sınıf adı gövdeye **sızmaz**.

### Para

```json
{ "amount": 12550, "currency": "TRY" }
```

Tutar **kuruş** cinsinden tamsayıdır ([ADR-006](../adr/006-money-as-minor-unit.md)). İstemci hesap yapmaz, biçimlendirir.

### Zaman

Tüm zaman damgaları UTC ve ISO-8601. Gösterim zaman dilimi istemcinin işidir.

---

## Uç noktalar

### `GET /api/v1/products`

Vitrinde yayınlanmış ürünleri listeler. **Kimlik doğrulaması gerektirmez** (`SecurityConfig` içinde açıkça `permitAll`).

**Sorgu parametreleri**

| Parametre | Tip | Varsayılan | Kural |
|---|---|---|---|
| `page` | `integer` | `0` | Sıfırdan başlar; negatif değer 400 döner |
| `size` | `integer` | `20` | En az 1; üst sınır sunucuda **50**'ye çekilir, aşan istek reddedilmez |

**Davranış**

- Yalnızca `PUBLISHED` ürünler döner; `DRAFT` ve `ARCHIVED` görünmez.
- Sıralama yayın tarihine göre yeniden eskiye; eşit tarihlerde ikincil ölçüt tanımlayıcıdır (sayfalar arası tutarlılık).
- Aralık dışı sayfa boş içerik ve doğru toplam döner; hata üretmez.

**200 yanıtı** — `PageResponseProductSummary`

Yalnızca satıcısı `APPROVED` olan `PUBLISHED` ürünler döner ([ADR-041](../adr/041-storefront-filters-seller-status-in-query.md)). Her ürün varsa `coverPhoto` (`ProductPhotoView`) taşır; fotoğrafı olmayan eski seed ürünlerinde boştur. Kapaklar tek ek sorguyla gelir ([ADR-052](../adr/052-storefront-cover-second-query-detail-by-slug.md)).

```json
{
  "items": [
    {
      "slug": "sultani-uzum",
      "name": "Sultani Üzüm",
      "unit": "KILOGRAM",
      "unitPrice": { "amount": 4250, "currency": "TRY" },
      "stockQuantity": 120,
      "seller": {
        "slug": "ornek-uretici-manisa",
        "displayName": "Örnek Üretici (Manisa)",
        "city": "Manisa"
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

| Alan | Tip | Not |
|---|---|---|
| `slug` | `string` | Ürünün kalıcı tanımlayıcısı |
| `name` | `string` | Ürün adı |
| `unit` | `enum` | `KILOGRAM` / `PIECE` / `BUNCH` |
| `unitPrice` | `Money` | Birim fiyat |
| `stockQuantity` | `integer` | Sıfırsa stokta değil |
| `seller` | `SellerSummary` | Üretici: `slug`, `displayName`, `city` |

Sözleşmedeki tüm bu alanlar **zorunludur** (`required`): istemci tipleri optional alan görmez.

**400 yanıtı** — `ApiError`. Geçersiz `page`/`size` değerleri.

### `GET /api/v1/health`

Operasyonel sağlık kontrolü; konteyner healthcheck'i bunu kullanır. Spring Boot Actuator bilinçli olarak kullanılmıyor ([ADR-002](../adr/002-dependency-budget.md) bütçesi).

```json
{ "status": "UP" }
```

Derin kontrol (veritabanı erişimi) **yoktur**: sağlık ucu, bağımlılıkları yavaşken konteyneri yeniden başlatan bir mekanizmaya dönüşmemeli.

### `GET /v3/api-docs` · `GET /v3/api-docs.yaml`

Üretilmiş sözleşmenin kendisi. Swagger UI paketi alınmadığı için yalnızca spec uçları vardır.

---

## Kimlik ve oturum uçları

Kimlik modeli: [ADR-008](../adr/008-jwt-auth-with-opaque-refresh.md), [ADR-017](../adr/017-email-verification-non-blocking-and-2fa-switch.md), [ADR-018](../adr/018-refresh-token-rotation-with-family-revocation.md)…[ADR-026](../adr/026-verification-link-resend-requires-session.md), parola sıfırlama [ADR-033](../adr/033-password-reset-token-separate-table-one-hour.md)…[ADR-036](../adr/036-password-reset-marks-email-verified.md), oturum açıkken parola değiştirme [ADR-087](../adr/087-password-change-endpoint-requires-current-password.md)…[ADR-090](../adr/090-password-change-page-bff-clears-cookies.md). Korunan uçlar `Authorization: Bearer <erişim token'ı>` ister (sözleşmede `bearerAuth` güvenlik şeması). Erişim token'ı HS256 imzalı bir JWT'dir, 15 dakika geçerlidir ve `sub` (hesap kimliği), `email`, `roles` iddialarını taşır.

| Uç | Kimlik | Başarı | Hatalar | Hız sınırı |
|---|---|---|---|---|
| `POST /api/v1/accounts` | açık | `202` `RegistrationAccepted` | 400 | IP: saatte 10 |
| `POST /api/v1/accounts/verifications` | açık | `204` | 400 (`VALIDATION_FAILED`, `AUTH_TOKEN_INVALID`) | IP: 15 dk'da 20 |
| `POST /api/v1/accounts/password-resets` | açık | `202` `PasswordResetRequested` | 400 | IP: saatte 10 · e-posta: saatte 3 |
| `POST /api/v1/accounts/password-resets/confirmations` | açık | `204` | 400 (`VALIDATION_FAILED`, `AUTH_TOKEN_INVALID`) | IP: 15 dk'da 20 |
| `GET /api/v1/accounts/me` | Bearer | `200` `AccountView` | 401 | - |
| `POST /api/v1/accounts/me/verification-requests` | Bearer | `202` (gövdesiz) | 401 | hesap: saatte 3 |
| `POST /api/v1/accounts/me/password-changes` | Bearer | `204` | 400 (`VALIDATION_FAILED`: `currentPassword`, `newPassword`), 401 | hesap: 15 dk'da 5 |
| `PUT /api/v1/accounts/me/locale` | Bearer | `204` | 400 (`VALIDATION_FAILED`: `locale`), 401 | - |
| `POST /api/v1/sessions` | açık | `200` `SessionTokens` · `202` `LoginChallengeIssued` | 400, 401 | IP + e-posta: 15 dk'da 10 |
| `POST /api/v1/sessions/challenge` | açık | `200` `SessionTokens` | 400, 401 (`AUTH_CODE_INVALID`), 429 | IP + e-posta: 15 dk'da 10 |
| `POST /api/v1/sessions/refresh` | açık (yenileme token'ı gövdede) | `200` `SessionTokens` (kullanılmış token 30 sn içinde tekrar gelirse de; [ADR-069](../adr/069-refresh-token-reuse-grace-window.md)) | 400 (`AUTH_TOKEN_INVALID`) | IP: 15 dk'da 60 |
| `DELETE /api/v1/sessions/current` | açık (yenileme token'ı gövdede) | `204` | 400 (token eksik) | - |

Hız sınırı aşılınca her uç `429 RATE_LIMITED` döner.

### `POST /api/v1/accounts`

```json
{ "email": "alici@ornek.test", "password": "cok-gizli-parola" }
```

E-posta biçimi geçerli olmalı (baştaki/sondaki boşluk reddedilir, kırpma istemcinin işi); parola 10–200 karakter. Yanıt **her durumda** aynıdır ([ADR-021](../adr/021-identity-responses-do-not-reveal-accounts.md)):

```json
{ "message": "E-posta adresinize bir bağlantı gönderdik. Hesabınızla hemen giriş yapabilirsiniz." }
```

Yeni adrese doğrulama bağlantısı (`<web>/verify-email?token=…`), kayıtlı adrese "hesabınız zaten var" e-postası gider. Kayıtlı adresle yapılan kayıt mevcut hesabı değiştirmez.

### `POST /api/v1/accounts/verifications`

```json
{ "token": "<e-postadaki bağlantının token'ı>" }
```

Hesabı doğrulanmış işaretler ve token'ı tüketir. Girişi etkilemez.

### `POST /api/v1/accounts/password-resets`

```json
{ "email": "alici@ornek.test" }
```

Yanıt **her durumda** aynıdır ([ADR-034](../adr/034-password-reset-request-reveals-nothing.md)):

```json
{ "message": "Bu adrese kayıtlı bir hesap varsa parola sıfırlama bağlantısı gönderdik. Bağlantı 1 saat geçerlidir." }
```

Adres kayıtlıysa hesabın dilinde bir sıfırlama bağlantısı (`<web>/reset-password?token=…`) gider; bağlantı 1 saat geçerlidir ve önceki bağlantıları geçersiz kılar ([ADR-033](../adr/033-password-reset-token-separate-table-one-hour.md)). Kayıtsız adrese e-posta **gönderilmez**.

### `POST /api/v1/accounts/password-resets/confirmations`

```json
{ "token": "<e-postadaki bağlantının token'ı>", "password": "yeni-cok-gizli-parola" }
```

Parola kuralları kayıttakiyle aynıdır (10–200 karakter); doğrulama hatası token'ı tüketmez. Başarıda ([ADR-035](../adr/035-password-reset-signs-out-everywhere-no-auto-login.md), [ADR-036](../adr/036-password-reset-marks-email-verified.md)):

- parola değişir ve token tüketilir;
- hesabın bütün yenileme token'ları iptal edilir, açık giriş kodları kapanır;
- e-posta doğrulanmış işaretlenir;
- hesabın dilinde "parolanız değişti" bildirimi gider.

Yanıt gövdesizdir ve **oturum açmaz**: kullanıcı `POST /sessions` ile yeni parolasıyla giriş yapar. Geçersiz, kullanılmış ve süresi dolmuş token aynı `400 AUTH_TOKEN_INVALID` hatasını alır.

### `POST /api/v1/accounts/me/password-changes`

```json
{ "currentPassword": "eski-cok-gizli-parola", "newPassword": "yeni-cok-gizli-parola" }
```

Hesap oturumdan gelir. Yeni parola kayıt kurallarına uyar (10–200 karakter) ve mevcut paroladan farklı olmalıdır ([ADR-087](../adr/087-password-change-endpoint-requires-current-password.md)). Mevcut parola yanlışsa `400 VALIDATION_FAILED` ve `currentPassword` alanında hata döner; 401 dönmez, oturum geçerlidir ([ADR-088](../adr/088-wrong-current-password-is-field-error-rate-limited-per-account.md)). Hız sınırı parola denetiminden önce uygulanır ve başarılı istekleri de sayar.

Başarıda ([ADR-089](../adr/089-password-change-signs-out-everywhere.md)):

- parola değişir;
- hesabın **bütün** yenileme token'ları, isteği yapan oturumunki dahil, iptal edilir; açık giriş kodları kapanır;
- hesabın dilinde "parolanız değişti" bildirimi gider.

Yanıt gövdesizdir ve **oturum açmaz**. E-posta doğrulama durumu değişmez (sıfırlamadan farkı).

### `PUT /api/v1/accounts/me/locale`

```json
{ "locale": "EN" }
```

Hesabın e-posta dilini değiştirir ([ADR-098](../adr/098-account-locale-updated-by-explicit-endpoint.md)); değerler `TR` / `EN`. Hesap oturumdan gelir. Dil aynıysa kayıt değişmez. Yanıt gövdesizdir; yanıt mesajları her zaman isteğin dilindedir. Girişte dil güncellemesi de sürer ([ADR-032](../adr/032-account-locale-stored-and-refreshed-on-login.md)).

### `GET /api/v1/accounts/me`

Güncel kayıttan okunur ([ADR-024](../adr/024-roles-in-jwt-claims-fresh-me-endpoint.md)); hesap kimliği dönmez.

```json
{ "email": "alici@ornek.test", "roles": ["ROLE_CUSTOMER"], "emailVerified": false }
```

### `POST /api/v1/accounts/me/verification-requests`

Gövde almaz. Oturum sahibinin kayıtlı adresine yeni bir doğrulama bağlantısı gönderir; e-posta zaten doğrulanmışsa bir şey göndermez. Yanıt her durumda `202`'dir.

### `POST /api/v1/sessions`

```json
{ "email": "alici@ornek.test", "password": "cok-gizli-parola" }
```

İki başarılı yanıt **durum koduyla** ayrışır:

- `200` — ikinci faktör kapalı (varsayılan):

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
  "accessTokenExpiresAt": "2026-10-07T12:15:00Z",
  "refreshToken": "q3Jx0…",
  "refreshTokenExpiresAt": "2026-11-06T12:00:00Z"
}
```

- `202` — ikinci faktör açık; token yok, e-postaya 6 haneli kod gitti:

```json
{ "message": "E-posta adresinize bir giriş kodu gönderdik.", "expiresAt": "2026-10-07T12:10:00Z" }
```

Parola doğruysa hesabın dili isteğin diliyle (`Accept-Language`) güncellenir; giriş kodu e-postası bu dilde gider ([ADR-032](../adr/032-account-locale-stored-and-refreshed-on-login.md)). Hatalı parola hesabın dilini değiştirmez.

### `POST /api/v1/sessions/challenge`

```json
{ "email": "alici@ornek.test", "code": "042917" }
```

Kod hesabın en son açık girişimine karşı doğrulanır ([ADR-025](../adr/025-second-factor-code-bound-to-account.md)). Başarıda `SessionTokens`. Girişim başına 5 hatalı denemeden sonra doğru kod da `429` alır.

### `POST /api/v1/sessions/refresh`

```json
{ "refreshToken": "q3Jx0…" }
```

Sunulan token tüketilir; aynı oturum zincirinde yeni bir çift döner ve erişim token'ı hesabın **güncel** rollerini taşır. Kullanılmış bir token tekrar sunulursa zincirin tamamı iptal edilir ([ADR-018](../adr/018-refresh-token-rotation-with-family-revocation.md)).

### `DELETE /api/v1/sessions/current`

```json
{ "refreshToken": "q3Jx0…" }
```

Zincirin tamamını iptal eder. Bilinmeyen veya zaten iptal edilmiş token için de `204` döner. Erişim token'ı en fazla 15 dakika daha geçerli kalır.

---

## `web` BFF akışı

Tarayıcı `api`'ye hiç konuşmaz ve token görmez ([ADR-023](../adr/023-web-bff-cookies-and-header-csrf.md)). Formlar `web`'in kendi Route Handler'larına konuşur; onlar `api`'yi çağırır ve token'ları `HttpOnly` çerezlere yazar.

| `web` yolu | Yöntem | `api` karşılığı | Not |
|---|---|---|---|
| `/api/auth/register` | POST | `POST /accounts` | |
| `/api/auth/login` | POST | `POST /sessions` | Yanıt `{"outcome":"AUTHENTICATED"}` veya `{"outcome":"CHALLENGE_REQUIRED","message":…}`; token yok |
| `/api/auth/challenge` | POST | `POST /sessions/challenge` | |
| `/api/auth/verification-request` | POST | `POST /accounts/me/verification-requests` | |
| `/api/auth/password-reset-request` | POST | `POST /accounts/password-resets` | |
| `/api/auth/password-reset` | POST | `POST /accounts/password-resets/confirmations` | Çerez yazmaz; tarayıcı `/login?reset=1`'e gider |
| `/api/auth/password-change` | POST | `POST /accounts/me/password-changes` | Başarıda oturum çerezlerini siler, hatada dokunmaz; tarayıcı `/login?reset=1`'e gider ([ADR-090](../adr/090-password-change-page-bff-clears-cookies.md)) |
| `/api/account/locale` | PUT | `PUT /accounts/me/locale` | Gövde `{"locale":"en"}`. Oturum çerezi yoksa `api`'ye gitmez; `api` 401 dönerse de `204`: dil menüsünün en iyi çaba eşitlemesi ([ADR-099](../adr/099-locale-menu-syncs-account-best-effort.md)) |
| `/api/auth/logout` | POST | `DELETE /sessions/current` | `api`'ye ulaşılamasa da çerezler silinir |
| `/api/auth/refresh?next=<yol>` | GET | `POST /sessions/refresh` | Gezinti; başarıda `next`'e, reddedilirse `/login?next=`'e 307 |
| `/api/seller/application` | POST, PUT | `POST`, `PUT /sellers/me` | `multipart/form-data`; yalnızca formun beş alanı iletilir |
| `/api/seller/application/document` | GET | `GET /sellers/me/document` | Akış olarak iletim; koruma başlıkları aynen geçer; 401'de `/api/auth/refresh?next=`'e 307 |
| `/api/admin/sellers/{id}/{action}` | POST | `POST /admin/sellers/{id}/{action}` | `action` beyaz listede değilse 404, `api`'ye gidilmez |
| `/api/admin/sellers/{id}/document` | GET | `GET /admin/sellers/{id}/document` | Sahibin belge yoluyla aynı iletim |
| `/api/seller/products` | POST | `POST /sellers/me/products` | Yalnızca ilan alanları iletilir |
| `/api/seller/products/{id}` | PUT | `PUT /sellers/me/products/{id}` | |
| `/api/seller/products/{id}/photos` | POST | `POST /sellers/me/products/{id}/photos` | Multipart; yalnızca `photo` iletilir |
| `/api/seller/products/{id}/photos/{photoId}` | DELETE | `DELETE …/photos/{photoId}` | |
| `/api/seller/products/{id}/photos/order` | PUT | `PUT …/photos/order` | Yalnızca `photoIds` |
| `/api/seller/products/{id}/{action}` | POST | `POST …/{publish\|unpublish\|archive\|restore}` | Beyaz liste dışı eylem 404 |
| `/api/admin/products/{id}/{action}` | POST | `POST /admin/products/{id}/{remove\|reinstate}` | Beyaz liste dışı eylem 404 |
| `/media/product-photos/{key}` | GET | `GET /product-photos/{key}` | Herkese açık akış; önbellek başlıkları aynen geçer |

- **Çerezler:** `bahceden_at` (`path=/`) ve `bahceden_rt` (`path=/api/auth`); ikisi de `HttpOnly`, `Secure`, `SameSite=Lax`, ömürleri yenileme token'ı kadar.
- **CSRF:** POST'lar `Sec-Fetch-Site: same-origin` ister; yoksa `Origin` `Host` ile karşılaştırılır. Reddedilen istek `403 CROSS_SITE_REQUEST` alır (BFF'in kendi kodu, `api`'de yok). `api`'ye ulaşılamazsa BFF `502 SERVICE_UNAVAILABLE` döner.
- **Yenileme:** korunan sayfa erişim token'ının süresinin dolduğunu görünce kullanıcıyı önce `/session/refresh?next=` sayfasına yönlendirir; sayfa `/api/auth/refresh?next=` adresine tam sayfa gezintisiyle geçer (JavaScript yoksa "Devam et" bağlantısı). Ara sayfa gereklidir: uygulama içi bağlantıyla yapılan gezinti bir Route Handler'a yönlendirilirse Next.js istemci yönlendiricisi onu izleyemez ve boş sayfada kalır; sunucu da iki gezinti türünü ayırt edemez (yönlendirici başlıkları `headers()`'tan çıkarılır).
- **Sessiz yenileme ([ADR-070](../adr/070-silent-session-refresh-on-any-page.md)):** üst başlık oturumun süresinin dolduğunu görünce her sayfada `/api/auth/refresh?next=<yol>&silent=1` adresine tam sayfa gezintisi yapar. Sessiz kipte başarısız yenileme çerezleri siler ve kullanıcıyı giriş ekranına değil aynı sayfaya oturumsuz döndürür; `api`'ye ulaşılamazsa oturum silinmeden aynı sayfaya dönülür. Sekmeler `bahceden-session-refresh` Web Locks kilidiyle sıraya girer; kilidi bekleyen sekme yenileme yapmadan sayfasına döner. Bir sekme dakikada en fazla bir kez dener (`sessionStorage` zaman damgası).
- **İstemci adresi:** BFF `X-Forwarded-For` başlığını `api`'ye iletir; `api` başlığa yalnızca iç ağdan gelen proxy için güvenir.

---

## Satıcı başvurusu uçları

Change `add-seller-onboarding`; kararlar [ADR-037](../adr/037-seller-application-with-document-and-manual-approval.md), [ADR-040](../adr/040-seller-application-is-the-seller-record.md)…[ADR-046](../adr/046-admin-surface-in-web-first-admin-by-sql.md).

| Uç | Kimlik | Başarı | Hatalar | Hız sınırı |
|---|---|---|---|---|
| `GET /api/v1/sellers/me` | Bearer | `200` `SellerSelfView` | 401, 404 (`SELLER_NOT_FOUND`) | - |
| `POST /api/v1/sellers/me` | Bearer | `201` `SellerSelfView` + `Location` | 400, 401, 409 (`SELLER_ALREADY_EXISTS`), 422 (`SELLER_EMAIL_NOT_VERIFIED`), 429 | hesap: saatte 5 |
| `PUT /api/v1/sellers/me` | Bearer | `200` `SellerSelfView` | 400, 401, 404, 409 (`SELLER_INVALID_TRANSITION`), 429 | hesap: saatte 5 (başvuruyla ortak) |
| `GET /api/v1/sellers/me/document` | Bearer | `200` PDF / JPEG / PNG | 401, 404 | - |
| `GET /api/v1/admin/sellers?status=&page=&size=` | Bearer + `ROLE_ADMIN` | `200` `PageResponseAdminSellerSummary` | 400, 401, 403 (`AUTH_FORBIDDEN`) | - |
| `GET /api/v1/admin/sellers/{id}` | Bearer + `ROLE_ADMIN` | `200` `AdminSellerDetail` | 401, 403, 404 | - |
| `GET /api/v1/admin/sellers/{id}/document` | Bearer + `ROLE_ADMIN` | `200` PDF / JPEG / PNG | 401, 403, 404 | - |
| `POST /api/v1/admin/sellers/{id}/approve` | Bearer + `ROLE_ADMIN` | `200` `AdminSellerDetail` | 401, 403, 404, 409 | - |
| `POST /api/v1/admin/sellers/{id}/reject` | Bearer + `ROLE_ADMIN` | `200` `AdminSellerDetail` | 400 (gerekçe), 401, 403, 404, 409 | - |
| `POST /api/v1/admin/sellers/{id}/suspend` | Bearer + `ROLE_ADMIN` | `200` `AdminSellerDetail` | 400 (gerekçe), 401, 403, 404, 409 | - |
| `POST /api/v1/admin/sellers/{id}/reinstate` | Bearer + `ROLE_ADMIN` | `200` `AdminSellerDetail` | 401, 403, 404, 409 | - |

### `POST /api/v1/sellers/me` ve `PUT /api/v1/sellers/me`

`multipart/form-data` alır ([ADR-042](../adr/042-seller-application-single-multipart-request.md)). Alanlar:

| Alan | Kural |
|---|---|
| `displayName` | 2–160 karakter |
| `city` | 2–80 karakter; serbest metin |
| `phone` | Türkiye numarası; boşluk, tire ve parantez temizlenir, `0` / `90` / `+90` öneki kabul edilir, `+90XXXXXXXXXX` olarak saklanır |
| `documentType` | `CKS_CERTIFICATE` veya `TAX_CERTIFICATE` |
| `document` | PDF, JPEG veya PNG; en çok 10 MB. Tür, dosyanın ilk baytlarından tespit edilir; istemcinin bildirdiği tür ve dosya adı yok sayılır ([ADR-045](../adr/045-seller-documents-table-content-sniffing-protected-serving.md)) |

Dosya türü, boyut ve telefon hataları da `fieldErrors` içinde ilgili alan adıyla döner. Başvuru kaydı `PENDING` açar. Yeniden gönderim yalnızca `REJECTED` kayıtta yapılabilir; yeni bir belge ekler ve kaydı yeniden `PENDING` yapar. `slug` değişmez.

### Belge yanıtları

Sahibin ve admin'in belge uçları aynı başlıklarla döner:

- tespit edilen `Content-Type`;
- `X-Content-Type-Options: nosniff`;
- `Content-Security-Policy: sandbox`;
- `Cache-Control: no-store, private`;
- `Content-Disposition: inline; filename="belge.<uzantı>"`.

Başka bir satıcının belgesine sahip yolundan ulaşılamaz: kayıt token'daki hesaptan bulunur.

### Admin kararları

| Eylem | Geçiş | Gövde | Rol etkisi |
|---|---|---|---|
| `approve` | `PENDING` → `APPROVED` | yok | `ROLE_SELLER` eklenir |
| `reject` | `PENDING` → `REJECTED` | `{"reason": "…"}` (10–500) | yok |
| `suspend` | `APPROVED` → `SUSPENDED` | `{"reason": "…"}` (10–500) | `ROLE_SELLER` kaldırılır |
| `reinstate` | `SUSPENDED` → `APPROVED` | yok | `ROLE_SELLER` eklenir |

Her karar hesabın dilinde e-postayla bildirilir. Gönderim hatası kararı geri almaz. Rol erişim token'ına bir sonraki yenilemede yansır (en fazla 15 dakika; [ADR-044](../adr/044-seller-role-follows-status-with-query-gate.md)). `AdminSellerDetail.history` geçişleri eskiden yeniye, geçişi yapan hesabın e-postasıyla birlikte verir.

---

## Ürün ilanı uçları

Change `add-product-listings`; kararlar [ADR-047](../adr/047-listing-states-removed-and-named-transitions.md)…[ADR-053](../adr/053-admin-listing-moderation-shared-reason-dialog.md).

| Uç | Kimlik | Başarı | Hatalar | Hız sınırı |
|---|---|---|---|---|
| `GET /api/v1/products/{slug}` | açık | `200` `ProductDetail` | 404 (`PRODUCT_NOT_FOUND`) | - |
| `GET /api/v1/product-photos/{key}` | açık | `200` `image/jpeg`, `max-age=31536000, immutable` | 404 | - |
| `GET /api/v1/sellers/me/products` | `ROLE_SELLER` + onaylı satıcı | `200` `PageResponseSellerListingView` | 401, 403 (`AUTH_FORBIDDEN`, `SELLER_NOT_APPROVED`) | - |
| `POST /api/v1/sellers/me/products` | aynı | `201` `SellerListingView` + `Location` | 400, 401, 403 | - |
| `GET`, `PUT /api/v1/sellers/me/products/{id}` | aynı | `200` `SellerListingView` | 400, 401, 403, 404 | - |
| `POST /api/v1/sellers/me/products/{id}/photos` | aynı | `200` `SellerListingView` | 400 (`photo`), 401, 403, 404, 409 (`PRODUCT_PHOTO_LIMIT_REACHED`), 429 | hesap: saatte 60 |
| `DELETE /api/v1/sellers/me/products/{id}/photos/{photoId}` | aynı | `200` | 401, 403, 404, 409 (`PRODUCT_PHOTO_REQUIRED`) | - |
| `PUT /api/v1/sellers/me/products/{id}/photos/order` | aynı | `200` | 400 (`photoIds`), 401, 403, 404 | - |
| `POST /api/v1/sellers/me/products/{id}/{publish\|unpublish\|archive\|restore}` | aynı | `200` | 401, 403, 404, 409 | - |
| `GET /api/v1/admin/products?status=` | `ROLE_ADMIN` | `200` `PageResponseAdminProductSummary` | 400, 401, 403 | - |
| `POST /api/v1/admin/products/{id}/remove` | `ROLE_ADMIN` | `200` `AdminProductSummary` | 400 (gerekçe), 401, 403, 404, 409 | - |
| `POST /api/v1/admin/products/{id}/reinstate` | `ROLE_ADMIN` | `200` `AdminProductSummary` | 401, 403, 404, 409 | - |

- **İlan alanları:** ad 2–200; açıklama en çok 2000; birim `KILOGRAM` / `PIECE` / `BUNCH`; `priceAmount` 1–10.000.000 kuruş; `stockQuantity` 0–100.000. Para birimi her zaman TRY. `slug` oluşturmada addan üretilir ve sonra değişmez.
- **Fotoğraf:** JPEG veya PNG, en çok 10 MB ve 40 megapiksel. EXIF yönü uygulanır, iki boyutta JPEG'e yeniden kodlanır, meta veri atılır. Okunamayan dosya `400` döner ([ADR-050](../adr/050-photo-processing-exif-orientation-two-sizes.md)).
- **Kaldırma ve yeniden açma:** satıcıya hesabının dilinde e-postayla bildirilir. Satıcı kaldırılan ilanı düzenleyebilir ama yayına alamaz.

---

## Ödeme hesabı, sepet, adres ve sipariş uçları

Change `add-cart-and-checkout`; kararlar [ADR-054](../adr/054-split-payment-via-provider-port-provider-pending.md)…[ADR-058](../adr/058-flat-shipping-fee-per-seller-and-declared-handover.md) (çerçeve) ve [ADR-059](../adr/059-payment-and-order-packages-single-purchasability-rule.md)…[ADR-068](../adr/068-checkout-web-routes-server-cart-count.md).

| Uç | Kimlik | Başarı | Hatalar | Hız sınırı |
|---|---|---|---|---|
| `GET /api/v1/sellers/me/payout-account` | `ROLE_SELLER` + onaylı satıcı | `200` `PayoutAccountView` (maskeli) | 401, 403, 404 (`PAYOUT_ACCOUNT_NOT_FOUND`) | - |
| `PUT /api/v1/sellers/me/payout-account` | aynı | `200` `PayoutAccountView` | 400 (`taxId`, `taxOffice`, `iban`, `legalType`…), 401, 403, 422 (`PAYOUT_ACCOUNT_REJECTED`), 502 (`PAYMENT_PROVIDER_ERROR`) | - |
| `GET /api/v1/cart` | oturum | `200` `CartView` | 401 | - |
| `PUT /api/v1/cart/items/{productId}` | oturum | `200` `CartView` | 400 (`quantity`), 401, 404, 422 (`PRODUCT_NOT_PURCHASABLE`, `CANNOT_BUY_OWN_PRODUCT`, `CART_FULL`) | - |
| `DELETE /api/v1/cart/items/{productId}` | oturum | `200` `CartView` | 401 | - |
| `GET /api/v1/delivery/provinces` | açık | `200` `PageResponseDeliveryProvince` | - | - |
| `GET`, `POST /api/v1/addresses` | oturum | `200` liste / `201` `AddressView` + `Location` | 400 (`provinceCode`, `phone`…), 401, 422 (`ADDRESS_LIMIT_REACHED`) | - |
| `PUT`, `DELETE /api/v1/addresses/{id}` | oturum | `200` / `204` | 400, 401, 404 (`ADDRESS_NOT_FOUND`) | - |
| `POST /api/v1/orders` (+ `Idempotency-Key` başlığı) | oturum | `201` `OrderPlaced` + `Location` | 400 (`Idempotency-Key`, `addressId`), 401, 404 (`ADDRESS_NOT_FOUND`), 422 (`ORDER_EMAIL_NOT_VERIFIED`, `CART_EMPTY`, `CART_HAS_UNAVAILABLE_ITEMS`, `ADDRESS_NOT_DELIVERABLE`), 429, 502 | hesap: saatte 20 |
| `POST /api/v1/orders/{orderNumber}/payment` | oturum | `200` `OrderDetail` | 401, 404 (`ORDER_NOT_FOUND`), 502 | - |
| `GET /api/v1/orders` | oturum | `200` `PageResponseOrderSummary` | 400, 401 | - |
| `GET /api/v1/orders/{orderNumber}` | oturum | `200` `OrderDetail` | 401, 404 | - |
| `POST /api/v1/orders/{orderNumber}/seller-orders/{id}/cancel` | oturum | `200` `OrderDetail` | 400, 401, 404, 409 (`ORDER_INVALID_TRANSITION`), 502 | - |
| `GET /api/v1/sellers/me/orders?status=` | `ROLE_SELLER` + onaylı satıcı | `200` `PageResponseSellerOrderSummary` | 400, 401, 403 | - |
| `GET /api/v1/sellers/me/orders/{id}` | aynı | `200` `SellerOrderDetail` | 401, 403, 404 (`SELLER_ORDER_NOT_FOUND`) | - |
| `POST /api/v1/sellers/me/orders/{id}/cancel` | aynı | `200` `SellerOrderDetail` | 400 (`reason`), 401, 403, 404, 409, 502 | - |

- **Vitrin:** `ProductSummary` ve `ProductDetail` `purchasable` taşır; `ProductDetail` ayrıca `dispatchDays` ve `id` (sepete ekleme için). `ListingRequest.dispatchDays` isteğe bağlıdır (1–3; yoksa yeni ilanda 2).
- **Sepet:** fiyat ve stok her okumada üründen gelir; satıcı grubu başına `itemsTotal`, `shippingFee`, `freeShippingRemaining`, `dispatchDays`; sepet için `itemCount`, toplamlar ve `hasUnavailableItems`. Satın alınamaz hale gelen satır `available = false` ile kalır ve toplama girmez.
- **Kargo ücreti:** satıcı başına 9900 kuruş; satıcının ürün tutarı 100000 kuruş ve üzerindeyse 0. **Komisyon:** satıcı siparişi başına `min(tutar, yuvarla(tutar × %5) + 300)`, hakediş = tutar − komisyon. Değerler sipariş anında yazılır.
- **E-posta doğrulaması:** `POST /orders` doğrulanmamış hesapta `422 ORDER_EMAIL_NOT_VERIFIED` döner; durum güncel kayıttan okunur. Aynı `Idempotency-Key` ile tekrar kapıdan önce değerlendirilir. Sepet, adresler ve `POST /orders/{n}/payment` doğrulama istemez ([ADR-093](../adr/093-order-requires-verified-email.md)).
- **Ödeme:** `POST /orders` yanıtındaki `paymentRedirectUrl` adresine gidilir; dönüşte `POST /orders/{n}/payment` sonucu sağlayıcıdan doğrular. Stok bu anda kilitle düşer; yetmezse ödemenin tamamı iade edilir (`CANCELLED_OUT_OF_STOCK`). 30 dakikada tamamlanmayan ödeme `PAYMENT_EXPIRED`. Çağrı tekrarlanabilir.
- **İptal:** yalnızca `NEW` durumundaki satıcı siparişi; alıcıda gerekçe isteğe bağlı, satıcıda 10–500 karakter zorunlu. Satıcı siparişinin ürün tutarı ve kargo ücreti iade edilir, stok geri eklenir.
- **Gizlilik:** satıcı yanıtlarında alıcının adı, telefonu ve açık adresi yoktur; yalnızca il ve ilçe. Ödeme hesabında IBAN ve kimlik numarası maskelidir.

---

## Kargo gönderisi uçları

Change `add-shipments`; kararlar [ADR-057](../adr/057-shipping-via-integrator-port-integrator-pending.md) (çerçeve) ve [ADR-071](../adr/071-shipping-package-integrator-port-fake-only.md)…[ADR-078](../adr/078-shipping-notifications-email-only-three-events.md).

| Uç | Kimlik | Başarı | Hatalar | Hız sınırı |
|---|---|---|---|---|
| `POST /api/v1/sellers/me/orders/{id}/shipment` | `ROLE_SELLER` + onaylı satıcı | `201` `Shipment` (açıldı) / `200` `Shipment` (zaten açıktı) | 400 (`packageSize`), 401, 403, 404 (`SELLER_ORDER_NOT_FOUND`), 409 (`ORDER_INVALID_TRANSITION`), 422 (`SHIPMENT_SENDER_PHONE_MISSING`), 502 (`SHIPPING_INTEGRATOR_ERROR`) | - |
| `GET /api/v1/sellers/me/orders/{id}/shipment/label` | aynı | `200` PDF veya PNG, korumalı başlıklarla | 401, 403, 404 | - |
| `GET /api/v1/admin/seller-orders?status=` | `ROLE_ADMIN` | `200` `PageResponseAdminSellerOrderSummary` | 400 (`status`: yalnızca `DELIVERY_FAILED`, `REFUNDED`, `CLOSED`), 401, 403 | - |
| `GET /api/v1/admin/seller-orders/{id}` | aynı | `200` `AdminSellerOrderDetail` | 401, 403, 404 | - |
| `POST /api/v1/admin/seller-orders/{id}/resolution` | aynı | `200` `AdminSellerOrderDetail` | 400 (`decision`, `reason`), 401, 403, 404, 409 (`ORDER_INVALID_TRANSITION`), 502 (`PAYMENT_PROVIDER_ERROR`) | - |

- **Gönderi açma:** yalnızca `NEW` satıcı siparişinde; gönderici satıcının adı, ili ve telefonu (paket şubeye teslim edilir), alıcı siparişin adres kopyası. Koli boyutunun desi karşılığı yapılandırmadadır, ücreti etkilemez. Gönderi açılması satıcı siparişini değiştirmez.
- **Durum:** zamanlanmış sorgu (`bahceden.shipping.poll-interval`) açık gönderileri entegratörden okur. `HANDED_OVER` → satıcı siparişi `SHIPPED` (alıcıya e-posta), `DELIVERED` → `DELIVERED`, `DELIVERY_FAILED` → `DELIVERY_FAILED` (alıcıya ve satıcıya e-posta). `IN_TRANSIT` yalnızca gönderi durumudur.
- **Görünüm:** `SellerOrderDetail.shipment` ve `BuyerSellerOrderView.shipment` (`Shipment`: firma, takip numarası, durum, koli boyutu, zamanlar). Etiket yalnızca satıcıya, ayrı uçtan.
- **İptal (değişti):** gönderisi açılmış `NEW` siparişte önce gönderi entegratörde iptal edilir, sonra iade; entegratör reddederse 502 `SHIPPING_INTEGRATOR_ERROR` ve hiçbir şey değişmez. `SHIPPED` ve sonrası 409.
- **Satıcı listesi:** `status` süzgeci artık `NEW`, `SHIPPED`, `DELIVERED`, `DELIVERY_FAILED`, `REFUNDED`, `CLOSED`, `CANCELLED` kabul eder.
- **Admin kararı:** `REFUND` ürün + kargo ücretini iade eder (`refunds.reason = UNDELIVERED`), stok geri eklenmez; `CLOSE` gerekçe ister (10–500), para hareketi yok. Detayda alıcının adı ve telefonu var, açık adres yok.

## Ayıplı ürün talebi uçları

Change `add-defect-claims`; kararlar [ADR-056](../adr/056-cancel-before-shipment-and-defect-refund.md) (çerçeve) ve [ADR-079](../adr/079-defect-claim-in-order-package-one-per-seller-order.md)…[ADR-086](../adr/086-defect-claim-web-surfaces.md).

| Uç | Kimlik | Başarı | Hatalar | Hız sınırı |
|---|---|---|---|---|
| `POST /api/v1/orders/{orderNumber}/seller-orders/{id}/claim` (multipart: `lines[n].orderItemId`, `lines[n].quantity`, `description`, `photos`) | Oturum (siparişin alıcısı) | `201` `OrderDetail` + `Location` | 400 (`lines`, `description`, `photos`), 401, 404 (`ORDER_NOT_FOUND`, `SELLER_ORDER_NOT_FOUND`), 409 (`CLAIM_NOT_ALLOWED`: teslim edilmedi veya talep zaten var), 422 (`CLAIM_WINDOW_CLOSED`), 429 | Hesap başına saatte 10 |
| `POST /api/v1/orders/{orderNumber}/seller-orders/{id}/claim/accept` | aynı | `200` `OrderDetail` | 401, 404 (`CLAIM_NOT_FOUND`), 409 (`CLAIM_INVALID_TRANSITION`), 502 (`PAYMENT_PROVIDER_ERROR`) | - |
| `POST /api/v1/orders/{orderNumber}/seller-orders/{id}/claim/escalate` | aynı | `200` `OrderDetail` | 401, 404, 409 | - |
| `POST /api/v1/sellers/me/orders/{id}/claim/response` (`decision`: `ACCEPT` / `OFFER` / `REJECT`, `lines`, `reason`) | `ROLE_SELLER` + onaylı satıcı | `200` `SellerOrderDetail` | 400, 401, 403, 404 (`CLAIM_NOT_FOUND`), 409 (`CLAIM_INVALID_TRANSITION`), 502 | - |
| `POST /api/v1/sellers/me/orders/{id}/claim/preview` (`lines`; boşsa istenenin tamamı) | aynı | `200` `ClaimSettlementView` | 400, 401, 403, 404 | - |
| `GET /api/v1/claims/{claimId}/photos/{position}?size=CARD` / `LARGE` (varsayılan `LARGE`) | Oturum (talebin alıcısı, satıcısı veya admin) | `200` JPEG, `no-store` + `nosniff` | 401, 404 (`CLAIM_NOT_FOUND`: yok veya taraf değil) | - |
| `GET /api/v1/admin/claims?status=` | `ROLE_ADMIN` | `200` `PageResponseAdminClaimSummary` | 400 (`status`), 401, 403 | - |
| `GET /api/v1/admin/claims/{id}` | aynı | `200` `AdminClaimDetail` | 401, 403, 404 | - |
| `POST /api/v1/admin/claims/{id}/preview` | aynı | `200` `ClaimSettlementView` | 400, 401, 403, 404 | - |
| `POST /api/v1/admin/claims/{id}/decision` (`decision`: `REFUND` / `REJECT`, `lines`, `reason`) | aynı | `200` `AdminClaimDetail` | 400, 401, 403, 404, 409 (`CLAIM_INVALID_TRANSITION`), 502 | - |

- **Açma:** satıcı siparişi `DELIVERED` olmalı ve gönderinin teslim anından bu yana `bahceden.claims.window` (48 saat) dolmamış olmalı. Satıcı siparişi başına tek talep; satırlar o satıcı siparişine ait, adet 1…satın alınan; açıklama 10–1000 karakter; 1–3 fotoğraf (ilan fotoğrafı hattı: döndürme, iki boyut, konum bilgisi atılır).
- **Durumlar:** `OPEN` → satıcı kabul ederse `REFUNDED`, kısmi teklif verirse `OFFERED`, reddederse `REJECTED`. `OFFERED` / `REJECTED` → alıcı kabul ederse `REFUNDED` / `CLOSED`, taşırsa `ESCALATED`. `ESCALATED` → admin `REFUNDED` veya `CLOSED`. Süreler: satıcı 48 saatte yanıtlamazsa `ESCALATED`; alıcı 48 saatte yanıtlamazsa teklif uygulanır (`REFUNDED`) veya ret kesinleşir (`CLOSED`). Süreleri zamanlanmış iş işler (`bahceden.claims.sweep-interval`).
- **Para:** iade = Σ birim fiyat × adet; bütün satırlar tam adetle iade edilirse kargo ücreti de döner. Komisyon kalan ürün tutarı üzerinden yeniden hesaplanır, sabit ücret yalnızca tam iadede geri alınır. İade satıcının payından `refunds.reason = DEFECT` ile yapılır; stok geri eklenmez. Önizleme uçları yan etkisizdir ve aynı hesabı döner.
- **Görünüm:** `BuyerSellerOrderView.claim` ve `claimableUntil` (talep açılabilecek son an); `SellerOrderDetail.claim`, `commissionAfterClaim`, `payoutAfterClaim`. `OrderItemView.id` talep satırı seçimi için eklendi. Admin detayında alıcının adı, telefonu ve adresi yoktur.
- **E-postalar:** talep açıldı → satıcıya; satıcı yanıtladı → alıcıya; admin'e taşındı → iki tarafa; sonuçlandı → iki tarafa ([ADR-085](../adr/085-defect-claim-emails.md)).

---

## Henüz olmayan uçlar

Oturum açıkken parola değiştirme uçları **yoktur**; karara bağlandı, change bekliyor. Gerçek kargo entegratörü ve durum bildirimi (webhook) entegratör seçimine bağlı (KALANLAR A2).

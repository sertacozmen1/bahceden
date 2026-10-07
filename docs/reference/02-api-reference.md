# API Referansı

**Son doğrulama:** 2026-10-07 · Kaynak: `shared/contracts/openapi.yaml`

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
| `INTERNAL_ERROR` | 500 | Beklenmeyen hata; ayrıntı yalnızca sunucu log'unda |

- `message` kullanıcıya gösterilebilir ve **isteğin dilindedir**: `Accept-Language` başlığına göre `tr` ya da `en`; başlık yoksa veya yalnızca desteklenmeyen dil bildiriyorsa Türkçe. Alan hataları (`fieldErrors[].message`) ve bilgilendirme yanıtlarındaki `message` alanları da aynı kurala uyar. `code` dilden bağımsızdır ([ADR-031](../adr/031-api-messages-by-accept-language.md)).
- `web` BFF her çağrıda sayfanın seçili dilini `Accept-Language` olarak iletir ([ADR-030](../adr/030-locale-from-cookie-then-header.md)). Hesaba giden e-postalar ise isteğin değil hesabın dilindedir ([ADR-032](../adr/032-account-locale-stored-and-refreshed-on-login.md)).
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

**200 yanıtı** — `PageResponse<ProductSummary>`

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

Kimlik modeli: [ADR-008](../adr/008-jwt-auth-with-opaque-refresh.md), [ADR-017](../adr/017-email-verification-non-blocking-and-2fa-switch.md), [ADR-018](../adr/018-refresh-token-rotation-with-family-revocation.md)…[ADR-026](../adr/026-verification-link-resend-requires-session.md). Korunan uçlar `Authorization: Bearer <erişim token'ı>` ister (sözleşmede `bearerAuth` güvenlik şeması). Erişim token'ı HS256 imzalı bir JWT'dir, 15 dakika geçerlidir ve `sub` (hesap kimliği), `email`, `roles` iddialarını taşır.

| Uç | Kimlik | Başarı | Hatalar | Hız sınırı |
|---|---|---|---|---|
| `POST /api/v1/accounts` | açık | `202` `RegistrationAccepted` | 400 | IP: saatte 10 |
| `POST /api/v1/accounts/verifications` | açık | `204` | 400 (`VALIDATION_FAILED`, `AUTH_TOKEN_INVALID`) | IP: 15 dk'da 20 |
| `GET /api/v1/accounts/me` | Bearer | `200` `AccountView` | 401 | - |
| `POST /api/v1/accounts/me/verification-requests` | Bearer | `202` (gövdesiz) | 401 | hesap: saatte 3 |
| `POST /api/v1/sessions` | açık | `200` `SessionTokens` · `202` `LoginChallengeIssued` | 400, 401 | IP + e-posta: 15 dk'da 10 |
| `POST /api/v1/sessions/challenge` | açık | `200` `SessionTokens` | 400, 401 (`AUTH_CODE_INVALID`), 429 | IP + e-posta: 15 dk'da 10 |
| `POST /api/v1/sessions/refresh` | açık (yenileme token'ı gövdede) | `200` `SessionTokens` | 400 (`AUTH_TOKEN_INVALID`) | IP: 15 dk'da 60 |
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

Yeni adrese doğrulama bağlantısı (`<web>/dogrulama?token=…`), kayıtlı adrese "hesabınız zaten var" e-postası gider. Kayıtlı adresle yapılan kayıt mevcut hesabı değiştirmez.

### `POST /api/v1/accounts/verifications`

```json
{ "token": "<e-postadaki bağlantının token'ı>" }
```

Hesabı doğrulanmış işaretler ve token'ı tüketir. Girişi etkilemez.

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
| `/api/auth/logout` | POST | `DELETE /sessions/current` | `api`'ye ulaşılamasa da çerezler silinir |
| `/api/auth/refresh?next=<yol>` | GET | `POST /sessions/refresh` | Gezinti; başarıda `next`'e, reddedilirse `/giris?next=`'e 307 |

- **Çerezler:** `bahceden_at` (`path=/`) ve `bahceden_rt` (`path=/api/auth`); ikisi de `HttpOnly`, `Secure`, `SameSite=Lax`, ömürleri yenileme token'ı kadar.
- **CSRF:** POST'lar `Sec-Fetch-Site: same-origin` ister; yoksa `Origin` `Host` ile karşılaştırılır. Reddedilen istek `403 CROSS_SITE_REQUEST` alır (BFF'in kendi kodu, `api`'de yok). `api`'ye ulaşılamazsa BFF `502 SERVICE_UNAVAILABLE` döner.
- **Yenileme:** korunan sayfa erişim token'ının süresinin dolduğunu görünce kullanıcıyı `/api/auth/refresh?next=` adresine yönlendirir.
- **İstemci adresi:** BFF `X-Forwarded-For` başlığını `api`'ye iletir; `api` başlığa yalnızca iç ağdan gelen proxy için güvenir.

---

## Henüz olmayan uçlar

Parola sıfırlama, satıcı olma, satıcı ürün yönetimi, sepet, sipariş, ödeme ve gönderim uçları **yoktur**. Parola sıfırlama sıradaki en küçük dilimdir; diğerleri `openspec/KALANLAR.md` maddelerine bağlıdır.

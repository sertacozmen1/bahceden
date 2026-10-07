# Nasıl Yapılır: Korunan bir API ucu ekleme

**Özet:** Bu rehber, oturum isteyen (rol veya sahiplik kontrollü) yeni bir `api` ucunu nasıl ekleyeceğinizi ve hangi testlerle sabitleyeceğinizi gösterir. Genel akış (DTO → sözleşme → `web` tipleri → ekran) için önce [Yeni API ucu ekleme](yeni-api-ucu-ekleme.md) rehberine bakın; bu rehber yalnızca **yetkilendirme** katmanını anlatır.

## Ön koşullar

- [Yerel geliştirme ortamı](yerel-gelistirme.md) kurulu; `docker compose up` ile `db`, `api`, `mailpit` ayakta.
- Okunmuş kararlar: [ADR-007](../adr/007-query-level-ownership-scoping.md) (sahiplik sorgu seviyesinde), [ADR-024](../adr/024-roles-in-jwt-claims-fresh-me-endpoint.md) (yetki JWT iddialarından), `AGENTS.md` §11.6 ve §11.7.
- Geliştirme hesabı: dev seed'deki `iki-adim@ornek.test` (ikinci faktör açık) veya kendi kaydettiğiniz bir hesap. Seed parolası `004-dev-seed-account.sql` yorumunda yazılıdır ve yalnızca yerel veritabanında geçerlidir.

---

## Adımlar

### 1. Ucu `SecurityConfig` içinde açıkça yetkilendirin

Varsayılan **deny**'dır: listede olmayan her uç reddedilir. Yeni uç `anyRequest().denyAll()` satırından **önce** eklenir (`api/src/main/java/com/bahceden/common/SecurityConfig.java`):

```java
// Oturum sahibi: geçerli erişim token'ı ister; rol fark etmez.
.requestMatchers(HttpMethod.GET, "/api/v1/accounts/me").authenticated()
// Rol isteyen uç: rol adı ROLE_ önekiyle saklanır, hasAuthority ile tam ad yazılır.
.requestMatchers(HttpMethod.POST, "/api/v1/seller/products").hasAuthority("ROLE_SELLER")
```

> [!NOTE]
> Roller token'daki `roles` iddiasından gelir ve `ROLE_` önekini zaten taşır; `hasRole("SELLER")` da çalışır ama ekip içinde tek biçim için `hasAuthority("ROLE_…")` tercih edilir.

### 2. Controller'da oturum sahibini token'dan alın

Hesap kimliği token'ın `sub` iddiasıdır. İstemciden gelen bir `accountId` parametresine **asla** güvenilmez (`AGENTS.md` §11.1):

```java
@GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
public AccountView currentAccount(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token) {
    return mapper.toView(service.currentAccount(Long.parseLong(token.getSubject())));
}
```

Operasyona sözleşmede güvenlik şemasını ekleyin; `web` tipleri ucun korunduğunu böyle görür:

```java
@Operation(operationId = "...", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
```

### 3. Sahiplik kontrolünü repository sorgusuna koyun

Başkasına ait bir kayıt `findById` sonrası `if` ile değil, **sorgunun kendisinde** dışlanır ([ADR-007](../adr/007-query-level-ownership-scoping.md)). Sonuç yoksa `404` döner: kaydın var olduğu bile sızmaz.

```java
Optional<Product> findByIdAndSellerId(Long id, Long sellerId);
```

### 4. Gerekiyorsa hız sınırı ekleyin

Kaba kuvvete veya e-posta göndermeye açık uçlar `RateLimiter` ile sınırlanır. Politika `RateLimitPolicy` enum'una eklenir (sihirli sayı yok); kontrol controller'da yapılır, çünkü istemci adresi HTTP katmanının bilgisidir ([ADR-022](../adr/022-in-memory-fixed-window-rate-limiting.md)):

```java
rateLimiter.check(RateLimitPolicy.VERIFICATION_REQUEST, Long.toString(accountId));
```

### 5. Dört zorunlu testi yazın

Her korunan uç için (`AGENTS.md` §11.7), `critical` etiketiyle ve gerçek PostgreSQL'e karşı:

| Test | Beklenen |
|---|---|
| Mutlu yol | `200`/`201`/`204` ve doğru gövde |
| Doğrulama hatası | `400 VALIDATION_FAILED`, `fieldErrors` dolu |
| Kimliksiz istek | `401 AUTH_REQUIRED` + `WWW-Authenticate: Bearer` |
| Başkasının kaydı / yetkisiz rol | `404` (sahiplik) veya `403` (rol) |

Token'ı testte gerçek giriş ucundan alın; elle imzalamak yalnızca süresi dolmuş veya yabancı anahtarlı token senaryoları içindir (bkz. `CurrentAccountTest`). Süreye bağlı davranış için `MutableClock` kullanın; gerçek bekleme yapmayın.

```bash
cd api && ./mvnw -B test -Dtest='CurrentAccountTest'
```

### 6. Sözleşmeyi yeniden üretin

```bash
cd api && ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.write=true
```

Üretilen `shared/contracts/openapi.yaml` diff'ini commit'e dahil edin.

### 7. `web` tarafında veriyi oturumla çekin

Sunucu tarafında oturumu `currentSession()` ile okuyun; korunan sayfanın kapısını `layout.tsx` içine `requireSignedIn("/yol")` olarak koyun. Böylece oturumsuz istek gerçek bir `307` ile `/giris`'e, süresi dolmuş token ise önce yenilemeye gider ([ADR-023](../adr/023-web-bff-cookies-and-header-csrf.md)). Erişim token'ını `apiGet(path, { accessToken })` ile iletin; token'ı asla istemci bileşenine prop olarak vermeyin.

---

## Uçtan uca elle deneme

```bash
# Giriş: ikinci faktörü kapalı bir hesapla token alın (yanıtı terminale yazdırmayın, değişkene alın)
TOKEN=$(curl -s -H 'Content-Type: application/json' \
  -d '{"email":"<hesap>@ornek.test","password":"<parola>"}' \
  http://localhost:8080/api/v1/sessions | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s -w ' [%{http_code}]\n' -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/accounts/me
curl -s -w ' [%{http_code}]\n' http://localhost:8080/api/v1/accounts/me   # 401 beklenir
```

## Olası hatalar

- **Hata:** Yeni uca istek `401 AUTH_REQUIRED` alıyor, token geçerli.
  **Çözüm:** Uç `SecurityConfig` listesinde yok; varsayılan deny kimliksiz isteği 401'e, kimlikli isteği 403'e çevirir. Ucu `denyAll` satırından önce ekleyin.
- **Hata:** Uygulama açılmıyor: `role should not start with 'ROLE_'`.
  **Çözüm:** `hasRole("ROLE_SELLER")` yazılmış; `hasRole` öneki kendisi ekler. `hasAuthority("ROLE_SELLER")` kullanın.
- **Hata:** Rol isteyen uç, rolü yeni verilmiş hesapla `403` dönüyor.
  **Çözüm:** Roller token'dadır; eski token yeni rolü taşımaz. Oturum yenilenince (en geç 15 dakika) gelir ([ADR-024](../adr/024-roles-in-jwt-claims-fresh-me-endpoint.md)).
- **Hata:** Testler arka arkaya koşunca `429 RATE_LIMITED`.
  **Çözüm:** Hız sınırı sayaçları paylaşılan test bağlamında yaşar. Test sınıfına `MutableClock.Config` ekleyin ve `@BeforeEach` içinde saati ilerletin (`clock.advance(Duration.ofDays(1))`).
- **Hata:** `api` açılmıyor: `bahceden.jwt.secret en az 32 bayt (256 bit) olmalıdır`.
  **Çözüm:** `BAHCEDEN_JWT_SECRET` tanımsız, kısa ya da Base64 değil. Compose dışında çalıştırıyorsanız `.env` dosyanıza `openssl rand -base64 32` çıktısını yazın (`api/.env.example`).

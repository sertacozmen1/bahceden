# add-account-and-session

> **Durum:** Proposed — kullanıcı onayı bekliyor (`AGENTS.md` §5.3).
> **Not:** `openspec` CLI bu makinede kurulu değil; artefaktlar `AGENTS.md` §5.2'deki yapıya göre elle yazıldı.
> **Bu change bütçe dışı bir paket gerektiriyor** (K7): aşağıdaki "Bağımlılık talebi" bölümü ayrıca onay ister.
> **Güncelleme (2026-10-06):** proje sahibinin yönlendirmesiyle e-posta doğrulaması **girişi engellemez** ve e-postanın kimlik akışına girmesi hesap bazlı `two_factor_enabled` anahtarına bağlanır ([ADR-017](../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md), ADR-008 §4'ü değiştirir). Artefaktlar bu karara göre güncellendi.

## Why

[ADR-008](../../../docs/adr/008-jwt-auth-with-opaque-refresh.md) kimlik modelini karara bağladı ama **tek satır kod yazılmadı**: bugün sistemde hesap, oturum ve yetkilendirme yok. `SecurityConfig` varsayılan deny uyguluyor ve yalnızca üç uç açık; yazma ucu olmadığı için bu bir sorun değil ama **her sonraki dilim buraya oturuyor**:

- Satıcı ürün ekleyemez: kim olduğunu bilmiyoruz ([ADR-007](../../../docs/adr/007-query-level-ownership-scoping.md) sahiplik kuralları güvenilir bir kimlik üzerine kurulu).
- Alıcı sipariş veremez: siparişin sahibi yok.
- `web` tarafındaki BFF katmanı (çerez + `Authorization: Bearer` köprüsü) hiç kurulmadı.

Bu dilim kimliği uçtan uca ayağa kaldırır: **kayıt → e-posta doğrulama → giriş → oturumlu ekran → yenileme → çıkış.** Bittiğinde korunan bir ekran gerçekten korunmuş olur ve bu testle sabitlenir.

## What Changes

**`api/` (yeni `account` domaini):**

- `accounts` (`email_verified`, `two_factor_enabled` dahil) + `account_roles` + `email_verification_tokens` + `refresh_tokens` + `login_challenges` tabloları (Liquibase `003`).
- `POST /api/v1/accounts` — kayıt. Hesap doğrudan **`ACTIVE`** açılır; doğrulama e-postası gider ama **girişi engellemez** ([ADR-017](../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md)). Yanıt, e-postanın kayıtlı olup olmadığını **açığa çıkarmaz**.
- `POST /api/v1/accounts/verifications` — doğrulama token'ını tüketir ve `email_verified` işaretler (giriş koşulu değildir).
- `POST /api/v1/sessions` — giriş. İkinci faktör kapalıysa (varsayılan) JWT erişim token'ı (15 dk) + opak yenileme token'ı döner; açıksa token dönmez ve e-posta ile tek kullanımlık kod gönderilir.
- `POST /api/v1/sessions/challenge` — ikinci faktör kodunu doğrular ve token'ları verir. Anahtar kapalı olduğu sürece bu yol kullanılmaz ama **uygulanmış ve testlidir** (K6: uygulanmamış dal bırakılmaz).
- `POST /api/v1/sessions/refresh` — yenileme (rotasyonlu, tekrar kullanım tespitli).
- `DELETE /api/v1/sessions/current` — çıkış; yenileme token'ı iptal edilir.
- `GET /api/v1/accounts/me` — oturum sahibinin kimliği ve rolleri. **Korunan ilk uç.**
- `POST /api/v1/accounts/me/verification-requests` — oturum sahibine yeni bir doğrulama bağlantısı gönderir (süresi dolan veya ulaşmayan bağlantı için). Oturum ister; e-posta almaz (design.md D9).
- JWT imzalama/doğrulama (`spring-boot-starter-oauth2-resource-server`, bütçede **mevcut**), BCrypt parola hash'i, hız sınırlama, e-posta gönderimi.

**`web/` (ince BFF + ekranlar):**

- Route Handler'lar: `/api/auth/login`, `/api/auth/logout`, `/api/auth/refresh`. Token'lar `HttpOnly` + `Secure` + `SameSite=Lax` çerezlere yazılır; tarayıcı JavaScript'ine **hiç** verilmez.
- Ekranlar: `/kayit`, `/giris` (ikinci faktör açıksa kod adımı aynı ekranda), `/dogrulama` (e-postadaki bağlantının indiği sayfa), `/hesabim` (korunan; doğrulama durumunu gösterir).
- `AppShell` oturum durumunu gösterir (giriş yapılmışsa e-posta + çıkış, değilse giriş bağlantısı).
- Sunucu tarafı render çerezi okur ve `api`'ye `Bearer` ile gider; erişim token'ı süresi dolmuşsa sessizce yenilenir.

**Sözleşme:** yeni DTO'lar ve yeni hata kodları (`AUTH_INVALID_CREDENTIALS`, `AUTH_TOKEN_INVALID`, `AUTH_CODE_INVALID`, `RATE_LIMITED`) sözleşmeye yansır; `openapi.yaml` yeniden üretilir. "Doğrulanmamış hesap" diye bir giriş hatası **yoktur** ([ADR-017](../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md)).

## Capabilities

| Capability | Durum | Kapsam |
|---|---|---|
| `account-identity` | ADDED | Kayıt, zorunlu e-posta doğrulama, oturum açma/yenileme/kapatma, oturum sahibinin kimliği |

## Impact

| Katman | Etki |
|---|---|
| `api/` | Yeni `account` domaini (ikinci faktör anahtarı ve e-posta kod akışı dahil); `SecurityConfig` JWT resource server olarak yapılandırılır ve `permitAll` listesi genişler; `ApiErrorCode` dört yeni kod alır; `common` altına e-posta gönderici ve hız sınırlayıcı |
| `web/` | Yeni `(account)` rota grubu + üç Route Handler; `apiClient` oturum başlığı ve 401 → yenileme akışını öğrenir; `AppShell` oturum bilgisini gösterir; `texts.ts` genişler |
| `shared/` | `openapi.yaml` yeniden üretilir: yedi yeni uç, yeni şemalar, yeni hata kodları |
| DB | Liquibase `003`: beş tablo + indeksler (`accounts.email` tekil, token ve kod hash'leri tekil, süre dolum sorguları için indeks) |
| `docs/` | ADR-018…025 (D1–D8), `01-domain-model.md` ER güncellemesi, `02-api-reference.md`, yeni how-to (korunan uç ekleme), yeni feature dosyası (`hesap-ve-oturum.md`), `00-current-state.md` |
| CI | Değişiklik yok; mevcut dört job yeni testleri kapsar |

## Bağımlılık talebi (K7 — onay gerekiyor)

Zorunlu e-posta doğrulaması e-posta göndermeyi gerektiriyor ve `api/` bugün e-posta gönderemiyor.

**İstenen:** `spring-boot-starter-mail` (`api/` runtime).

| K7 sorusu | Cevap |
|---|---|
| Platform neden yetmiyor? | Spring Boot çekirdeği SMTP istemcisi içermiyor. Alternatif, JavaMail'i elle kurmak veya soket üzerinden SMTP yazmak olurdu: aynı kütüphaneyi daha kötü biçimde elde etmek. |
| İki alternatif ve ret gerekçesi | **(a)** HTTP API'li sağlayıcı (Resend, Postmark, SES): yeni bir dış servis, hesap, API anahtarı ve sağlayıcı kilidi; sağlayıcı seçimi `KALANLAR` B4'e bağlı ve bu dilimi bloke eder. **(b)** E-posta göndermeyip doğrulama bağlantısını log'a yazmak: üretimde kırık bir akış demek, K6 ihlali. |
| Yük | Tek artifact, Spring Boot BOM ile sürümlenir, Apache-2.0, transitive olarak yalnızca `jakarta.mail`; çalışma zamanı etkisi ihmal edilebilir. |
| Katman / bağımlılık yönü | `api/` runtime; bağımlılık yönünü bozmaz (dış servise yalnızca `api` gider, §3). |

**Sağlayıcı seçimi bu dilimde yapılmıyor:** `spring-boot-starter-mail` herhangi bir SMTP sunucusuyla çalışır. Geliştirmede `mailpit` (compose'da mevcut), üretimde SMTP kimlik bilgileri ortamdan gelir. Sağlayıcı kararı `KALANLAR` B4/B5'te açık kalır.

## Non-goals

Bu change **kapsam dışı** bırakır:

- **Parola sıfırlama.** Token mekanizması bu dilimde kuruluyor (doğrulama için) ve sıfırlama onun üstüne oturacak; ama akış, ekranlar ve e-posta şablonu ikiye katlıyor. **Hemen sonraki dilim adayı.** Bugün parolasını unutan kullanıcı kilitlenir; gerçek kullanıcı olmadığı için kabul edilebilir bir boşluk.
- **Sosyal giriş** (Google vb.) — ADR-008 §5 bunu zaten kapsam dışı bıraktı.
- **Satıcı olma akışı:** `ROLE_SELLER` modelde var ama rolü veren akış `KALANLAR` A3'e bağlı. Kayıt olan herkes `ROLE_CUSTOMER` alır.
- **Hesap silme / KVKK veri dışa aktarma.** Yasal gereklilik; ayrı bir dilim ve karar konusu.
- **Yönetici ekranları** (kullanıcı listesi, rol atama).
- **SMS ve diğer bildirim kanalları** (`KALANLAR` B4).
- **İkinci faktörü açan uç veya ekran.** `two_factor_enabled` yalnızca veritabanından (veya dev seed'inden) açılır; kullanıcı tercihi olarak sunulması ayrı bir dilim.
- **Dağıtık hız sınırlama.** Tek örnek varsayımıyla bellek içi sınırlayıcı; ADR-008 bu sınırlamayı zaten kaydetti.

## Riskler

| Risk | Karşılık |
|---|---|
| Güvenlik yüzeyi bu dilimde en geniş: yanlış bir ayrıntı hesap devralmaya kadar gider | Her kural `critical` testle sabitlenir (enumeration, doğrulanmamış hesapla giriş, token tekrar kullanımı, iptal sonrası yenileme, hız sınırı). Testler kuralın kendisini korur |
| E-posta teslim edilemezse | **Kullanıcı kilitlenmiyor** ([ADR-017](../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md)): doğrulama girişi engellemiyor. Gönderim hatası kayda yazılır, kullanıcı aynı yanıtı alır. Bedeli: doğrulanmamış e-postayla hesap açılabiliyor; sipariş dilimi `email_verified` kapısını değerlendirmek zorunda |
| JWT imza anahtarının yönetimi | Ortam değişkeni, açılışta fail-fast doğrulama (K10). Anahtar rotasyonu en fazla 15 dk erişim token'ı geçersizliği demek; ADR-008'de kayıtlı |
| `web` BFF katmanı sunum katmanını kalınlaştırıyor | Üç Route Handler ve tek çerez yardımcısıyla sınırlı; iş kuralı taşımaz, yalnızca token taşır |
| Dilim büyük: dört tablo, yedi uç, dört ekran | `tasks.md` beş faza bölündü ve her faz tek başına çalışır durumda bırakılıyor (K5). Parola sıfırlama bilinçli olarak dışarıda tutuldu |

# [ADR-023] `web` BFF: iki HttpOnly çerez, başlık tabanlı CSRF savunması, yönlendirmeyle yenileme

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-account-and-session` design.md D6

---

## Context

[ADR-008](008-jwt-auth-with-opaque-refresh.md) token'ların tarayıcı JavaScript'ine hiç verilmemesini, `web`'in ince bir BFF olmasını ve CSRF korumasını şart koştu. Uygulamada iki Next.js gerçeği tasarımı şekillendirdi:

- **Server Component çerez yazamaz.** Sayfa render edilirken erişim token'ının süresinin dolduğu görülürse yenileme yerinde yapılamaz.
- Next.js konteynerde kendi kökenini makine adından kurar (`http://<konteyner>:3000`); tarayıcının gördüğü adresle eşleşmez.

## Decision

- **İki çerez**, ikisi de `HttpOnly` + `Secure` + `SameSite=Lax`:
  - `bahceden_at` (erişim, `path=/`). Ömrü JWT'nin 15 dakikası değil, **yenileme token'ı kadardır**: çerezin varlığı "oturum var" bilgisidir. Süresi dolmuş JWT hiçbir şeye yetki vermez.
  - `bahceden_rt` (yenileme, `path=/api/auth`): yalnızca kimlik Route Handler'larına gider, sayfa isteklerinde taşınmaz.
- **Route Handler'lar:** `POST /api/auth/{register,login,challenge,logout,verification-request}` ve `GET /api/auth/refresh`. Token'lar yanıt gövdesinde **yer almaz**, yalnızca çereze yazılır.
- **CSRF:** durum değiştiren her POST `Sec-Fetch-Site: same-origin` ister; başlık yoksa `Origin` **`Host` başlığıyla** karşılaştırılır; ikisi de yoksa istek reddedilir (`403 CROSS_SITE_REQUEST`). Ayrı bir CSRF token tablosu yoktur.
- **Yenileme yönlendirmeyle yapılır:** korunan sayfa `401` görürse kullanıcıyı `GET /api/auth/refresh?next=<yol>` adresine yönlendirir. Orada çerezler yenilenir ve kullanıcı geldiği yola döner; yenileme reddedilirse çerezler silinir ve `/login?next=` adresine gidilir. `api`'ye ulaşılamazsa oturum silinmez, hata durumu gösterilir. Bu uç üst düzey gezintiye her yerden izin verir (e-postadaki bağlantıdan gelen kullanıcı); siteler arası alt kaynak isteğini reddeder.
- Yönlendirmeler **göreli** `Location` ile yapılır; `next` yalnızca site içi bir yol olabilir (açık yönlendirme yok).
- Korunan sayfanın kapısı `layout.tsx` içindedir: yükleniyor sınırının dışında çalıştığı için yönlendirme gerçek bir `307` olur.
- BFF, `api` hız sınırı için istemci adres zincirini (`X-Forwarded-For`) iletir ([ADR-022](022-in-memory-fixed-window-rate-limiting.md)).

| Alternatif | Neden reddedildi |
|---|---|
| Çift gönderim (double submit) CSRF token'ı | Her formda ek alan ve eşitleme yükü; `SameSite=Lax` + başlık denetimi aynı saldırıyı kapatıyor. |
| `SameSite=Strict` | E-postadaki bağlantıdan gelen kullanıcı oturumsuz görünür. |
| Tek çerezde iki token | Yenileme token'ı her istekte gereksiz yere taşınır. |
| Yenilemeyi ara katmanda (middleware) şeffaf yapmak | Yenileme çerezinin yolunun `/` olmasını gerektirir ve paralel isteklerde aynı token'la eşzamanlı yenileme üretir: [ADR-018](018-refresh-token-rotation-with-family-revocation.md) bunu sızıntı sayıp oturumu kapatır. |
| Erişim çerezini JWT ile birlikte 15 dakikada sonlandırmak | Çerez düşünce sunucu "oturum yok" ile "yenilenmeli"yi ayıramaz; kullanıcı 15 dakikada bir giriş ekranına düşer. |

## Consequences

### Olumlu
- Token tarayıcı betiğine hiç ulaşmaz; XSS token çalamaz.
- Yenileme tek bir gezintide yapılır; eşzamanlı yenileme yarışı oluşmaz.
- Konteyner, ters proxy veya farklı alan adı arkasında yönlendirme ve köken denetimi doğru çalışır.

### Olumsuz
- Süresi dolmuş erişim token'ıyla gelinen korunan sayfa iki ek yönlendirme yapar (sayfa → yenileme → sayfa).
- Üst başlık oturum durumunu her istekte `api`'den okur; kökte çerez okunduğu için tüm sayfalar dinamik render edilir.

### Nötr / Trade-off
- `Sec-Fetch-Site` ve `Origin` göndermeyen çok eski tarayıcılar POST isteklerinde reddedilir; bu kitle hedef değil.

# [ADR-104] İki adımlı giriş kendi sayfasında: durum, mevcut parola ve tek düğme; doğrulanmamış e-postada önce doğrulama

**Tarih:** 2026-10-26
**Durum:** Accepted
**Kaynak:** `add-two-factor-toggle` design.md D2

---

## Context

[ADR-103](103-user-toggles-two-factor-with-current-password.md) iki adımlı girişi açan ve kapatan ucu ekler. Hesabım'ın Güvenlik bölümü bugün yalnızca parola değiştirme sayfasına bağlantı verir; parola formu kendi sayfasındadır ([ADR-090](090-password-change-page-bff-clears-cookies.md)). Ödeme sayfası, doğrulanmamış e-postada form yerine doğrulama yönlendirmesi gösterir ([ADR-094](094-checkout-page-shows-verification-panel.md)).

## Decision

- **Hesabım → Güvenlik:** "Parolayı değiştir" bağlantısının yanında "İki adımlı giriş" bağlantısı ve içinde durum rozeti ("Açık" / "Kapalı").
- **`/account/two-factor` sayfası:**
  - durum rozeti ve kısa açıklama (açıkken her girişte e-postaya kod gider);
  - mevcut parola alanı ve tek düğme: durum kapalıysa "Aç", açıksa "Kapat";
  - başarıda sayfa sunucudan yenilenir, yeni durum ve sonucu söyleyen bilgi mesajı görünür; oturum açık kalır;
  - yanlış parola alanın altına, diğer hatalar (doğrulanmamış e-posta, hız sınırı) formun üstüne bağlanır;
  - e-posta doğrulanmamış ve ikinci adım kapalıysa form yerine açıklama ve yeni doğrulama bağlantısı düğmesi. İkinci adım açıksa kapatma formu gösterilir.
  - Yükleniyor ve hata durumları Hesabım'ın mevcut sınırlarıyla (`/account/loading.tsx`, `(account)/error.tsx`); yetkisiz durum oturum kapısında.
- **BFF:** `PUT /api/account/two-factor`. Köken denetimi ve gövde okuma ortak adımla (`handleBffMutation`) yapılır. Açık mı kapalı mı olacağı mantıksal değer değilse istek `api`'ye gitmez. Hata gövdesi aynen iletilir; çerezlere dokunulmaz.

| Alternatif | Neden reddedildi |
|---|---|
| Hesabım içinde satır içi form | Hesabım'ı uzatır; parola sayfasıyla tutarsız. |
| Anahtar (toggle) bileşeni + parola penceresi | Tıklayınca hemen değişmeyen bir anahtar yanıltıcıdır; iki adımlı etkileşim, aynı sonuç. |
| Doğrulanmamış e-postada formu gösterip sunucu hatasını beklemek | Kullanıcı parolasını boşuna yazar; doğru eylem doğrulamadır. |

## Consequences

### Olumlu
- Durum Hesabım'da görünür; değişiklik tek odaklı bir sayfada yapılır.
- Doğrulanmamış e-postalı kullanıcı, ikinci adımı açmadan önce ne yapması gerektiğini görür.

### Olumsuz
- Hesabım'dan bir sayfa daha uzakta.

### Nötr / Trade-off
- Bilgi mesajı istemci durumundadır; sayfa yeniden açılınca yalnızca durum rozeti kalır.

# [ADR-090] `web`: parola değiştirme ayrı sayfada; BFF başarıda çerezleri siler, giriş ekranına yönlendirir

**Tarih:** 2026-10-17
**Durum:** Accepted
**Kaynak:** `add-password-change` design.md D4

---

## Context

Hesabım sayfası kimlik, e-posta doğrulama, alışveriş, satıcılık ve çıkış bölümlerini taşıyor. Token'lar `HttpOnly` çerezlerde, BFF katmanında yaşar ([ADR-008](008-jwt-auth-with-opaque-refresh.md), [ADR-023](023-web-bff-cookies-and-header-csrf.md)).

[ADR-089](089-password-change-signs-out-everywhere.md) gereği başarılı değişiklikten sonra tarayıcıdaki yenileme token'ı da iptal edilmiştir. Çerezde kalırsa ilk yenileme denemesi reddedilir ve kullanıcı açıklamasız bir oturum sonuyla karşılaşır.

Sıfırlama akışı giriş ekranına `/login?reset=1` ile döner ve orada "parolanız değişti" mesajı gösterilir.

## Decision

- Parola formu kendi sayfasındadır: `/account/password`. Sayfa Hesabım kapısının altındadır; yükleniyor durumu Hesabım'ın iskeletini kullanır.
- Hesabım'da "Güvenlik" başlıklı bölümde tek bir "Parolayı değiştir" bağlantısı vardır.
- Form iki alan içerir: mevcut parola ve yeni parola. "Yeni parola tekrar" alanı yoktur (kayıt ve sıfırlama formlarıyla aynı). Sayfanın giriş cümlesi, bu cihaz dahil bütün oturumların kapanacağını önceden söyler.
- BFF Route Handler `/api/auth/password-change`:
  - CSRF savunmasından geçer;
  - erişim token'ını çerezden alır;
  - `api` 204 dönerse oturum çerezlerini siler;
  - hata durumunda çerezlere dokunmaz ve hata gövdesini aynen iletir.
- Başarıda form `/login?reset=1`'e gider ve `router.refresh()` çağırır; üst başlık çerezsiz yeniden çizilir (çıkış düğmesiyle aynı yol).

| Alternatif | Neden reddedildi |
|---|---|
| Hesabım içinde katlanır form | Sayfanın yükünü artırır; hata durumunu bütün sayfayla paylaşır. |
| Diyalog | Parola yöneticileri diyalogdaki formları daha zayıf tanır; geri tuşu ve doğrudan bağlantı çalışmaz. |
| Çerezleri istemcide çıkış akışıyla silmek | İkinci istek ve iptal edilmiş token için gereksiz bir `logout` çağrısı. |
| "Yeni parola tekrar" alanı | Projedeki hiçbir parola formunda yok; kural ipucu ve sunucu doğrulaması yeterli. |

## Consequences

### Olumlu
- Tarayıcıda iptal edilmiş token kalmaz; kullanıcı neden çıkış yaptığını giriş ekranında okur.
- Mevcut giriş ekranı mesajı yeniden kullanılır.

### Olumsuz
- Oturumsuz açılan sayfanın dönüş adresi `/account`'tur: kapı düzende olduğu için alt sayfanın yolunu bilmez. Siparişler ve adresler sayfaları da aynı davranır.

### Nötr / Trade-off
- Formun yeni parolayı tekrar sormaması, yazım hatasında kullanıcının sıfırlama akışına gitmesi demektir; bu, diğer formlarla tutarlılık için kabul edildi.

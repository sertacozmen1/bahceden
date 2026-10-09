# Proposal: change-session-persistence

## Why

Giriş yapan kullanıcı tarayıcıyı kapatıp açınca vitrinde oturumsuz görünüyor. Oturum çerezleri duruyor, ama erişim token'ı 15 dakikada doluyor ve oturum yalnızca korunan bir sayfada yenileniyor ([ADR-023](../../../../docs/adr/023-web-bff-cookies-and-header-csrf.md)). Büyük e-ticaret sitelerinde (Trendyol, Hepsiburada, Amazon) kullanıcı geri geldiğinde hangi sayfayı açarsa açsın oturumu açık bulur.

Her sayfada yenileme, aynı anda geri yüklenen sekmelerde eşzamanlı yenileme demektir. [ADR-018](../../../../docs/adr/018-refresh-token-rotation-with-family-revocation.md) bunu sızıntı sayıp kullanıcıyı oturumdan atar.

## What Changes

- `api`: kullanılmış yenileme token'ı 30 saniye içinde tekrar sunulursa aynı ailede yeni çift verilir. Pencere dışında aile iptali sürer.
- `web`: üst başlık süresi dolmuş oturumu gördüğü her sayfada sessiz yenileme yapar (`/api/auth/refresh?silent=1`). Sessiz yenileme başarısızsa kullanıcı giriş ekranına itilmez.
- `web`: yenilemeler sekmeler arasında Web Locks API ile sıraya girer; bekleyen sekme yenileme yapmadan döner.
- Korunan sayfaların ara sayfası (`/session/refresh`) aynı kilidi kullanır.

**Non-goals:**

- Hassas işlemler için yeniden doğrulama (step-up). Bugün oturum açıkken parola veya e-posta değiştirme yok.
- Oturum ömrünün değişmesi (30 gün, her rotasyonda yeniden).
- "Beni hatırla" seçeneği ve cihaz/oturum listesi.
- Ara katmanda (middleware) şeffaf yenileme.

## Capabilities

- **Modified:** `account-identity`: "Oturum yenileme ve sonlandırma" (tolerans penceresi), "Token'lar tarayıcı JavaScript'ine verilmez" (sessiz yenileme senaryoları).

## Impact

- `api`: `SessionService.refresh`, `RefreshToken.usedWithin`. Şema, sözleşme ve yeni uç yok.
- `web`: `SessionNav`, `SessionAutoRefresh` (yeni), `SessionRefreshRedirect`, `/api/auth/refresh` route, `/session/refresh` sayfası.
- Yeni paket yok (K7). Web Locks bir tarayıcı API'sidir.
- Güvenlik: tolerans penceresi sızıntı tespitini ilk 30 saniye için zayıflatır ([ADR-069](../../../../docs/adr/069-refresh-token-reuse-grace-window.md) Consequences).

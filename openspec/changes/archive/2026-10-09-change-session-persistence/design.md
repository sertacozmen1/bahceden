# Design: change-session-persistence

## Context

Oturum iki çerezde durur ve ömrü 30 gündür ([ADR-023](../../../../docs/adr/023-web-bff-cookies-and-header-csrf.md), [ADR-018](../../../../docs/adr/018-refresh-token-rotation-with-family-revocation.md)). Erişim token'ı 15 dakikada dolar. Yenileme bugün yalnızca korunan sayfada tetikleniyor. Araştırma notları:

- Büyük e-ticaret siteleri oturumu tarayıcı yeniden açıldığında her sayfada sürdürüyor; resmi süreleri kamuya açık değil.
- Okta yenileme rotasyonunda varsayılan 30 saniyelik (0–60) tolerans kullanıyor; Auth0 "reuse interval" sunuyor.
- Çok sekmeli yenileme yarışı için yaygın çözüm Web Locks API (`navigator.locks.request`).
- Hassas işlemlerde oturumu kapatmak yerine işlem bazlı yeniden doğrulama (step-up) yapılıyor.

## Decisions

### D1: Yenileme token'ında 30 saniyelik tekrar kullanım toleransı → [ADR-069](../../../../docs/adr/069-refresh-token-reuse-grace-window.md)

Kullanılmış token, ilk kullanımından itibaren 30 saniye içinde yeniden gelirse aynı ailede yeni çift verilir. Pencere dışında aile iptal edilir; iptal edilmiş veya süresi dolmuş token pencerede de reddedilir.

- **Gerekçe:** kilit desteklenmeyen tarayıcı ve yarıda kesilen yenileme meşru kullanıcıyı çıkarmasın.
- **Alternatif:** toleranssız kalmak (reddedildi: her sayfada yenilemeyle risk artıyor); aynı ardılı yeniden vermek (reddedildi: düz token saklanmıyor).

### D2: Her sayfada sessiz yenileme → [ADR-070](../../../../docs/adr/070-silent-session-refresh-on-any-page.md)

Üst başlık oturum durumunu "süresi dolmuş" görürse `SessionAutoRefresh` render eder. Bileşen `GET /api/auth/refresh?next=<yol>&silent=1` adresine tam sayfa gezintisi yapar. Sessiz kipte başarısızlık giriş ekranına değil aynı sayfaya oturumsuz döner. Bir sekme dakikada en fazla bir kez dener.

- **Gerekçe:** kullanıcı geri geldiğinde oturumu her sayfada açık bulsun; vitrinde başarısız yenileme kullanıcıyı giriş ekranına itmesin.
- **Alternatif:** middleware'de şeffaf yenileme (ADR-023'te reddedildi); erişim token'ının ömrünü uzatmak (reddedildi: çıkış ve rol değişikliği gecikir).

### D3: Sekmeler arası sıralama Web Locks ile → [ADR-070](../../../../docs/adr/070-silent-session-refresh-on-any-page.md)

Yenileme `bahceden-session-refresh` kilidi altında yapılır. Kilit doluysa sekme bekler ve kilit bırakılınca yenileme yapmadan dönüş yoluna gider. Kilidi tutan sekme sayfası kapanınca kilidi bırakır; kapanmazsa 10 saniye sonra bırakır.

- **Gerekçe:** aynı anda geri yüklenen sekmeler tek yenileme yapsın.
- **Alternatif:** `BroadcastChannel` / `localStorage` olayı (reddedildi: kilit semantiği elle kurulur).

## Risks / Trade-offs

- İlk 30 saniyede çalınan token tespit edilmez (D1).
- Süresi dolmuş oturumla açılan ilk sayfa iki yönlendirme ek yükü taşır; başlık kısa süre nötr görünür (D2).
- JavaScript kapalıysa sessiz yenileme yok; korunan sayfa yolu çalışmaya devam eder.

## Migration Plan

Şema ve sözleşme değişmez. Dağıtımdan sonra mevcut oturumlar aynen çalışır.

## Open Questions

- Oturum açıkken parola/e-posta değiştirme eklendiğinde step-up yeniden doğrulama o change'de değerlendirilecek.

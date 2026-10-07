# [ADR-022] Kimlik uçlarında bellek içi sabit pencere hız sınırı

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-account-and-session` design.md D5

---

## Context

[ADR-008](008-jwt-auth-with-opaque-refresh.md) giriş, kayıt ve sıfırlama uçlarının hız sınırlı olmasını şart koştu ve tek örnek varsayımıyla uygulama içine koydu. Bütçede hız sınırı kütüphanesi yok ([ADR-002](002-dependency-budget.md)); `api` tek sunucuda tek örnek çalışıyor.

`web` BFF arkasında `api`'ye gelen her istek web konteynerinin adresinden gelir. İstemci adresi `X-Forwarded-For` başlığındadır ve bu başlık sahte gönderilebilir.

## Decision

- Sayaç **sabit pencerelidir**, bellektedir (`ConcurrentHashMap`) ve "şimdi"yi enjekte edilen `Clock`'tan alır. Yeni paket yoktur.
- Politikalar tek yerde durur (`RateLimitPolicy`):

| Uç | Anahtar | Sınır |
|---|---|---|
| Kayıt | istemci adresi | saatte 10 |
| Giriş | istemci adresi + e-posta | 15 dakikada 10 |
| İkinci adım | istemci adresi + e-posta | 15 dakikada 10 (girişim başına ayrıca 5 hatalı kod) |
| E-posta doğrulama | istemci adresi | 15 dakikada 20 |
| Yeni doğrulama bağlantısı | hesap | saatte 3 |
| Yenileme | istemci adresi | 15 dakikada 60 |

- Aşımda `429` ve `RATE_LIMITED` döner.
- İstemci adresi `server.forward-headers-strategy: native` ile okunur: Tomcat `X-Forwarded-For` başlığına **yalnızca iç ağdaki** (özel IP aralığındaki) bir proxy'den gelirse güvenir. Dışarıdan gönderilen sahte başlık adresi değiştiremez. `web` BFF başlığı `api`'ye iletir.

| Alternatif | Neden reddedildi |
|---|---|
| Bucket4j / Caffeine | Bütçe dışı paket; bu ölçekte kazanç yok. |
| Redis tabanlı dağıtık sayaç | Yeni servis; yatay ölçekleme geldiğinde ADR ile gelir. |
| Kayan pencere / token kovası | Daha düzgün dağılım ama daha karmaşık; kimlik uçlarında sabit pencere yeterli. |
| Yalnızca e-posta anahtarı (girişte) | Saldırgan bir kurbanın e-postasıyla sınırı doldurup hesabını kilitleyebilir (hizmet engelleme). |
| Hız sınırını atlamak | Kimlik uçları kaba kuvvete açık kalır. |

## Consequences

### Olumlu
- Bağımlılık eklenmedi; pencere davranışı sabit saatle testte doğrulanıyor.
- İkinci faktör kodunun 10⁶'lık uzayı, girişim başına 5 deneme ile pratikte tahmin edilemez.

### Olumsuz
- **Örnek başına:** iki örnek çalışırsa sınır ikiye katlanır; yeniden başlatma sayaçları sıfırlar.
- **Adres güveni ağ düzenine bağlı:** Next.js istemcinin gönderdiği `X-Forwarded-For` başlığını korur. `web` doğrudan internete açılırsa sahte başlık BFF üzerinden `api`'ye taşınır ve adres tabanlı sınır atlatılabilir. Üretimde `web`'in önünde başlığı kendisi yazan bir reverse proxy gerekir (`openspec/KALANLAR.md` B5).
- Adres + e-posta anahtarı, çok sayıda adres kullanan bir saldırganı tek başına durdurmaz.

### Nötr / Trade-off
- Bellek, 10 000 anahtar aşılınca süresi geçmiş pencereler temizlenerek sınırlanır.

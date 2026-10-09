# [ADR-070] Oturum her sayfada sessizce yenilenir; sekmeler Web Locks ile sıraya girer

**Tarih:** 2026-10-09
**Durum:** Accepted
**Kaynak:** `change-session-persistence` design.md D2, D3. [ADR-023](023-web-bff-cookies-and-header-csrf.md)'ün "yenileme yalnızca korunan sayfada" kuralını genişletir; ADR-023'ün çerez, CSRF ve yönlendirme kuralları yürürlüktedir.

---

## Context

[ADR-023](023-web-bff-cookies-and-header-csrf.md)'e göre oturum yalnızca korunan bir sayfa erişim token'ının dolduğunu görünce yenileniyordu. Bu yüzden tarayıcıyı kapatıp açan kullanıcı vitrinde oturumsuz görünüyordu: başlıkta e-postası, sepeti ve çıkış düğmesi yerine nötr bir "Hesabım" bağlantısı vardı. Oturum ancak korunan bir sayfaya gidilince geri geliyordu.

Büyük e-ticaret sitelerinde (Trendyol, Hepsiburada, Amazon) bu durum şöyle görünüyor:

- Oturum tarayıcı kapanınca bitmez. Kullanıcı geri geldiğinde hangi sayfayı açarsa açsın oturumu açık görür.
- Yeniden giriş istenmez. Sitelerin oturum süreleri kamuya açık değil; bu bir gözlemdir.
- Hassas işlemlerde (ödeme, parola veya e-posta değişikliği) kullanıcıyı çıkarmak yerine o işlem için yeniden doğrulama istenir (step-up).

ADR-023 ara katmanda (middleware) şeffaf yenilemeyi reddetti. O gerekçe hâlâ geçerli: yenileme çerezinin yolu `/` olurdu ve paralel isteklerde eşzamanlı yenileme üretirdi.

## Decision

- **Tetikleyici:** üst başlık her sayfada oturum durumunu okur (ADR-023). Durum "süresi dolmuş" ise başlık bir istemci bileşeni (`SessionAutoRefresh`) render eder. Bileşen tarayıcıyı `GET /api/auth/refresh?next=<bulunduğu yol>&silent=1` adresine götürür. Başarılı yenilemeden sonra kullanıcı aynı sayfaya oturumu açık olarak döner.
- **Sessiz kip (`silent=1`):** yenileme reddedilirse çerezler silinir ve kullanıcı **giriş ekranına değil**, aynı sayfaya oturumsuz döner. Vitrini gezen biri giriş ekranına itilmez. `api`'ye ulaşılamazsa oturum silinmez ve yine aynı sayfaya dönülür. Korunan sayfaların yolu (ADR-023: başarısızlıkta giriş ekranı) değişmez.
- **Sekmeler arası sıra (Web Locks API):** yenileme `bahceden-session-refresh` kilidi altında yapılır. Kilit doluysa sekme yenileme yapmaz; kilit bırakılınca (diğer sekmenin sayfası yenilenmiş çerezlerle kapanınca) doğrudan dönüş yoluna gider. API'yi desteklemeyen tarayıcıda olası yarışı `api`'nin 30 saniyelik penceresi karşılar ([ADR-069](069-refresh-token-reuse-grace-window.md)).
- **Döngü koruması:** sessiz yenileme bir sekmede dakikada en fazla bir kez denenir. Son deneme zamanı `sessionStorage`'da tutulur; bu bir zaman damgasıdır, token değildir.
- Korunan sayfanın ara sayfası (`/session/refresh`) aynı kilit yardımcısını kullanır. Orada sessiz yenileme ayrıca başlatılmaz.
- **Step-up yeniden doğrulama bugün eklenmez.** Oturum açıkken parola veya e-posta değiştirme yok. Ödemede sağlayıcının 3D Secure adımı ikinci doğrulamadır. Bu işlemler eklendiğinde yeniden doğrulama o change'de değerlendirilir.

| Alternatif | Neden reddedildi |
|---|---|
| Ara katmanda (middleware) şeffaf yenileme | ADR-023'teki gerekçe: yenileme çerezi her isteğe gider, paralel isteklerde yarış oluşur. |
| Yenilemeyi istemcide `fetch` ile yapıp sayfayı yenilemek | Başlık zaten sunucuda render edilmiştir; sayfa yine yenilenmeli. Tam sayfa gezintisi tek adımdır ve ADR-023'ün mevcut ucunu kullanır. |
| Erişim token'ının ömrünü uzatmak (ör. 30 gün) | Çıkış ve rol değişikliği uzun süre etkisiz kalır ([ADR-008](008-jwt-auth-with-opaque-refresh.md), [ADR-044](044-seller-role-follows-status-with-query-gate.md)). |
| Sekmeleri `BroadcastChannel` veya `localStorage` olayıyla sıraya sokmak | Kilit semantiği elle kurulur. Web Locks sekme kapanınca kilidi kendiliğinden bırakır. |
| Hassas işlemler için hemen step-up eklemek | Bugün hassas işlem yüzeyi yok (yukarıda). Kullanılmayan dal bırakılmaz (K6). |

## Consequences

### Olumlu
- Tarayıcıyı kapatıp açan kullanıcı hangi sayfayı açarsa açsın oturumu açık görür.
- Vitrinde başarısız yenileme kullanıcıyı giriş ekranına itmez.
- Aynı anda açılan sekmeler tek yenileme yapar. Diğerleri yenileme ucuna hiç gitmez.

### Olumsuz
- Süresi dolmuş oturumla açılan ilk sayfa iki yönlendirme ek yükü taşır. Başlık kısa bir süre nötr görünür, sonra sayfa açık oturumla yeniden yüklenir.
- Sessiz yenileme JavaScript gerektirir. JavaScript kapalıysa davranış ADR-023'teki gibidir (korunan sayfada yenileme).
- 15 dakikadan uzun süre açık kalan bir sekmede bir sonraki sayfa gezintisi de sessiz yenileme yapar.

### Nötr / Trade-off
- Oturumun ömrü değişmez: yenileme token'ı 30 gün, her rotasyonda yeniden 30 gün ([ADR-018](018-refresh-token-rotation-with-family-revocation.md)).

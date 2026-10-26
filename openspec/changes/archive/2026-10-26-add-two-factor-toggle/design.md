# Design: add-two-factor-toggle

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2): D1 → ADR-103, D2 → ADR-104.
> Çerçeve:
> - [ADR-017](../../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md): hesap bazlı `two_factor_enabled`, açık yolu uygulanmış.
> - [ADR-025](../../../../docs/adr/025-second-factor-code-bound-to-account.md): kod hesaba bağlı, 10 dakika, 5 deneme.
> - [ADR-087](../../../../docs/adr/087-password-change-endpoint-requires-current-password.md), [ADR-088](../../../../docs/adr/088-wrong-current-password-is-field-error-rate-limited-per-account.md): oturum açıkken hassas değişiklik mevcut parolayı ister; yanlış parola alan hatasıdır ve hız sınırı hesap başınadır.
>
> ADR-017 §2'nin "anahtarı değiştiren bir uç veya ekran yoktur" cümlesi ve ADR-025'in son maddesi ADR-103 ile değişir. İki ADR'ye not düşülür; diğer kararları geçerlidir.

## Kararlar

### D1 · `api`: oturum sahibi ikinci adımı mevcut parolayla açar veya kapatır

- **Uç:** `PUT /api/v1/accounts/me/two-factor`, kimlik doğrulamalı.
  - Gövde `TwoFactorUpdate { enabled: boolean, currentPassword: string }`; ikisi de zorunlu.
  - Yanıt `204`.
- **Sıra:**
  1. Hız sınırı: hesap başına 15 dakikada 5 istek (`RateLimitPolicy.TWO_FACTOR_CHANGE`). Parola denetiminden önce uygulanır ve başarılı istekleri de sayar.
  2. Mevcut parola yanlışsa `400 VALIDATION_FAILED`, `currentPassword` alanında hata.
  3. `enabled: true` ve e-posta doğrulanmamışsa `422 TWO_FACTOR_EMAIL_NOT_VERIFIED`.
  4. Değer zaten istenen durumdaysa hiçbir şey değişmez, e-posta gitmez, `204`.
  5. Değişir. Kapatılıyorsa açık giriş kodları kapanır (`closeOpenChallenges`).
  6. Hesabın dilinde bildirim e-postası (açıldı / kapandı) ve parola sıfırlama bağlantısı. Gönderim hatası değişikliği geri almaz (ADR-019).
- **Oturumlar sürer.** Açmak mevcut oturumları kapatmaz; ikinci adım bir sonraki girişte devreye girer. Kapatmak da oturumları kapatmaz.
- **`GET /accounts/me`** yanıtına `twoFactorEnabled` eklenir (ekleyici).
- **Log:** yalnızca hesap kimliği ve yeni durum.

**Gerekçe:**
- Parola şartı: çalınmış bir oturum ikinci adımı tek başına kapatamamalı. Açmak için de istenir: bir başkasının oturumu açık bırakılmış cihazda ikinci adımı açıp sahibini kilitlemesi engellenir.
- Doğrulanmış e-posta şartı: kod o adrese gider. Doğrulanmamış adres yanlış yazılmış olabilir; ikinci adım açılırsa kullanıcı kendini kilitler.
- Oturumları kapatmamak: parola değiştirmeden farkı, bu değişikliğin bir saldırıya yanıt olmamasıdır. Açarken oturumları kapatmak kullanıcıyı yalnızca yeniden girişe zorlar. Kapatırken kapatmak ise saldırgana bir şey kaybettirmez; zaten parolayı biliyordur.
- Kapatırken açık giriş kodlarını kapatmak: kapalı anahtarla bekleyen kod anlamsızdır. Kodu ele geçiren biri, kapatıldıktan sonra da o kodla giremez.

**Reddedilen alternatifler:**
- (a) **Parolasız açıp kapatmak:** çalınmış oturum korumayı kaldırır.
- (b) **Açarken e-postaya kod gönderip onaylatmak:** doğrulanmış e-posta aynı kanıtı zaten verir; ikinci bir akış ve ekran ekler.
- (c) **İki ayrı uç (`POST …/enable`, `POST …/disable`):** tek alanlı bir durumdur; `PUT` ile aynı kurallar tek yerde.
- (d) **Değişiklikte bütün oturumları kapatmak:** bkz. gerekçe. Kullanıcı isterse parolasını değiştirerek kapatabilir.

### D2 · `web`: Hesabım'dan ayrı sayfa, mevcut parola ve tek düğme

- **Hesabım → Güvenlik:** "Parolayı değiştir" bağlantısının yanında ikinci adımın durumu ("Açık" / "Kapalı") ve `/account/two-factor` bağlantısı.
- **`/account/two-factor` sayfası:**
  - Kısa açıklama: açıkken her girişte e-postaya kod gider.
  - E-posta doğrulanmışsa form: mevcut parola alanı ve tek düğme, durum açıksa "Kapat", kapalıysa "Aç".
  - E-posta doğrulanmamışsa form yerine açıklama ve `ResendVerificationButton` (ödeme sayfasındaki desen, ADR-094).
  - Başarıda sayfa sunucudan yenilenir, yeni durum ve bir bilgi mesajı görünür. Hata mesajı ilgili alana bağlanır.
  - Dört durum: yükleniyor, hata, yetkisiz (kapı), boş durum yok (sayfa her zaman tek hesabı gösterir).
- **BFF:** `PUT /api/account/two-factor`, `handleBffMutation` ile. Hata gövdesi aynen iletilir; çerezlere dokunulmaz.

**Gerekçe:**
- Parola formu da kendi sayfasında ([ADR-090](../../../../docs/adr/090-password-change-page-bff-clears-cookies.md)); Hesabım tek odakta kalır.
- Tek düğme: iki durumlu bir ayardır. Anahtar (toggle) bileşeni parola isteyen bir eylem için yanıltıcıdır, çünkü tıklayınca hemen değişmez.

**Reddedilen alternatifler:**
- (a) **Hesabım içinde satır içi form:** Hesabım'ı uzatır; parola sayfasıyla tutarsız.
- (b) **Anahtar bileşeni + açılır parola penceresi:** iki adımlı etkileşim, aynı sonuç.

## Yetkilendirme ve kişisel veri

- Uç kimlik doğrulamalıdır; hesap token'dan gelir. Başka hesap adreslenemez.
- Parola yalnızca karşılaştırılır; log'a yazılmaz.
- Bildirim e-postası yalnızca hesabın kendi adresine gider.

## Test etkisi

- **`TwoFactorToggleTest` (`critical`):**
  - açma: doğru parola ve doğrulanmış e-posta → anahtar açılır, bildirim e-postası hesabın dilinde gider, mevcut oturum yenilenebilir kalır, sonraki giriş iki adımlıdır;
  - kapatma: açık giriş kodu artık kabul edilmez, sonraki giriş tek adımlıdır;
  - yanlış parola → `currentPassword` alan hatası, anahtar değişmez;
  - doğrulanmamış e-postayla açma → `422`, anahtar değişmez;
  - aynı değer → e-posta yok;
  - eksik alan → `400`; token yok → `401`; hız sınırı → `429`;
  - `GET /accounts/me` `twoFactorEnabled`'ı döner.
- **`OpenApiContractTest`:** sözleşme yeniden üretilir. Mevcut `AccountView` testleri yeni alanla güncellenir.
- **`web`:** `TwoFactorForm` (başarı, alan hatası eşlemesi, 422 mesajı), sayfa (doğrulanmamış e-postada form yok, durum metni), BFF Route Handler (köken denetimi, iletim), Hesabım Güvenlik bölümü.

## Risks / Trade-offs

- **E-postasını kaybeden kullanıcı** ikinci adım açıksa giremez. Bugün parola sıfırlama da e-postaya bağlı; kurtarma süreci ayrı bir konudur.
- **Parolayı bilen saldırgan** ikinci adımı kapatabilir. Bildirim e-postası kullanıcıyı uyarır.

## Migration Plan

Şema değişmez. Ekleyici sözleşme değişikliği. Geri alma: uç ve sayfa kaldırılır; açılmış anahtarlar açık kalır ve giriş akışı değişmeden çalışır.

## Open Questions

Yok.

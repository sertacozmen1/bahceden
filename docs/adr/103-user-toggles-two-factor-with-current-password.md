# [ADR-103] Oturum sahibi iki adımlı girişi mevcut parolasıyla açar veya kapatır; açmak doğrulanmış e-posta ister

**Tarih:** 2026-10-26
**Durum:** Accepted
**Kaynak:** `add-two-factor-toggle` design.md D1; proje sahibi onayladı (kimlik doğrulama modeli, `AGENTS.md` §9)

---

## Context

E-posta ile ikinci adım hesap bazlı `two_factor_enabled` anahtarıyla çalışır; açık yolu uygulanmış ve testlidir ([ADR-017](017-email-verification-non-blocking-and-2fa-switch.md), [ADR-025](025-second-factor-code-bound-to-account.md)). İki ADR de anahtarı değiştiren bir uç veya ekran olmadığını kaydetmişti; anahtar yalnızca veritabanından açılıyordu (`00-current-state.md` L13).

Oturum açıkken yapılan hassas değişiklik mevcut parolayı ister ve yanlış parola alan hatasıdır; hız sınırı hesap başınadır ([ADR-087](087-password-change-endpoint-requires-current-password.md), [ADR-088](088-wrong-current-password-is-field-error-rate-limited-per-account.md)).

## Decision

- Yeni uç `PUT /api/v1/accounts/me/two-factor`, kimlik doğrulamalı. Gövde `TwoFactorUpdate { enabled, currentPassword }`, ikisi de zorunlu. Yanıt `204`.
- Sıra:
  1. hız sınırı: hesap başına 15 dakikada 5 istek (`TWO_FACTOR_CHANGE`); parola denetiminden önce, başarılı istekleri de sayar;
  2. mevcut parola yanlışsa `400 VALIDATION_FAILED`, `currentPassword` alanında;
  3. açmak için e-posta doğrulanmamışsa `422 TWO_FACTOR_EMAIL_NOT_VERIFIED`. Kapatmak doğrulama istemez;
  4. değer zaten istenen durumdaysa hiçbir şey değişmez ve e-posta gitmez;
  5. değişir. Kapatılıyorsa açık giriş kodları kapanır;
  6. hesabın dilinde bildirim e-postası (açıldı / kapandı) ve parola sıfırlama bağlantısı gider.
- Açık oturumlar iki yönde de sürer. İkinci adım bir sonraki girişte devreye girer.
- `GET /accounts/me` yanıtına `twoFactorEnabled` eklenir.
- ADR-017 §2'deki "anahtarı değiştiren bir uç veya ekran yoktur" cümlesinin ve ADR-025'in aynı anlamdaki son maddesinin yerini alır. O ADR'lerin diğer kararları geçerlidir.

| Alternatif | Neden reddedildi |
|---|---|
| Parolasız açıp kapatmak | Çalınmış oturum korumayı kaldırır ya da sahibini kilitler. |
| Açarken e-postaya kod gönderip onaylatmak | Doğrulanmış e-posta aynı kanıtı zaten verir; ikinci bir akış ve ekran ekler. |
| İki ayrı uç (`enable` / `disable`) | Tek alanlı bir durum; `PUT` kuralları tek yerde toplar. |
| Değişiklikte bütün oturumları kapatmak | Açarken yalnızca yeniden giriş zorunluluğu getirir. Kapatırken saldırgana bir şey kaybettirmez, çünkü parolayı zaten biliyordur. Kullanıcı isterse parolasını değiştirerek oturumları kapatır. |
| Kapatmayı da doğrulanmış e-postaya bağlamak | Doğrulanmamış adresle ikinci adımı açık kalan kullanıcı kendini kilitler. |

## Consequences

### Olumlu
- Kullanıcı hesabını parolasına ek olarak e-postasıyla koruyabilir.
- Çalınmış bir oturum ikinci adımı tek başına kapatamaz; kapatılırsa sahibine bildirim gider.

### Olumsuz
- E-postasına erişimi kaybeden kullanıcı, ikinci adım açıksa giremez. Parola sıfırlama da aynı e-postaya gider; kurtarma süreci ayrı bir konudur.
- Parolayı bilen saldırgan ikinci adımı kapatabilir; savunma bildirim e-postasıdır.

### Nötr / Trade-off
- TOTP, SMS ve kurtarma kodları kapsam dışıdır (ADR-025; KALANLAR B4).

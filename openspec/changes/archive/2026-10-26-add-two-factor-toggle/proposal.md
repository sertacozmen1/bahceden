# add-two-factor-toggle

**Durum:** Onaylandı (2026-10-26, proje sahibi), uygulandı ve kullanıcı onayıyla archive edildi (2026-10-26).

> Bu change [`00-current-state.md`](../../../../docs/reference/00-current-state.md) L13 sınırlamasını kapatır. E-posta ile ikinci faktör uygulanmış ve testlidir ([ADR-017](../../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md), [ADR-025](../../../../docs/adr/025-second-factor-code-bound-to-account.md)). Ama anahtarı değiştiren bir uç veya ekran yoktur; yalnızca veritabanından açılır. Bu change, ADR-017 §2'deki "anahtarı değiştiren bir uç veya ekran yoktur" maddesinin ve ADR-025'in aynı anlamdaki son maddesinin yerini alır. Kimlik doğrulama modelini değiştirdiği için onay ister (`AGENTS.md` §9). `KALANLAR.md`'deki hiçbir maddeye bağlı değildir.

## Why

Hesabını korumak isteyen kullanıcı bugün ikinci faktörü açamaz. Parolası başka bir sitede sızmış biri için, hesabın tek koruması o paroladır. Satıcının ödeme bilgileri (IBAN) ve alıcının adresleri hesapta durur. İkinci faktörün akışı hazır ve testli; eksik olan, kullanıcının onu açıp kapatabilmesidir.

## What Changes

- **`api`:**
  - Yeni uç `PUT /api/v1/accounts/me/two-factor`, gövde `{"enabled": true, "currentPassword": "…"}`, yanıt `204`.
  - Açmak ve kapatmak **mevcut parolayı** ister. Yanlış parola, parola değiştirmedeki gibi `currentPassword` alanında doğrulama hatası döner ([ADR-088](../../../../docs/adr/088-wrong-current-password-is-field-error-rate-limited-per-account.md)).
  - Açmak **doğrulanmış e-posta** ister. Doğrulanmamışsa `422 TWO_FACTOR_EMAIL_NOT_VERIFIED` döner: kodun gideceği adresin kullanıcıya ait olduğu kanıtlanmamıştır.
  - Değişiklik hesabın dilinde e-postayla bildirilir ("ikinci adım açıldı / kapandı; siz yapmadıysanız parolanızı sıfırlayın").
  - Kapatmak hesabın açık giriş kodlarını da kapatır. Açık oturumlar her iki yönde de sürer.
  - Değer zaten istenen durumdaysa hiçbir şey değişmez ve e-posta gitmez. Parola yine denetlenir.
  - Hız sınırı hesap başına 15 dakikada 5 istektir (parola değiştirmeyle aynı); parola denetiminden önce uygulanır.
  - `GET /accounts/me` yanıtına `twoFactorEnabled` alanı eklenir.
- **`web`:**
  - Hesabım → Güvenlik bölümü ikinci adımın durumunu gösterir ve yeni sayfaya `/account/two-factor` bağlantı verir.
  - Sayfada mevcut parola alanı ve tek düğme var: "Açık" ise "Kapat", "Kapalı" ise "Aç". Başarıda durum güncellenir ve bir bilgi mesajı görünür.
  - E-postası doğrulanmamış kullanıcıya form yerine açıklama ve yeni doğrulama bağlantısı düğmesi gösterilir (ödeme sayfasındaki desen).
  - BFF `PUT /api/account/two-factor`.
- **Sözleşme:** yeni uç, yeni şema `TwoFactorUpdate`, `AccountView.twoFactorEnabled`, yeni hata kodu. Hepsi ekleyici; kırıcı değil.

## Capabilities

- **Değişen:** `account-identity`.
  - "E-posta ile ikinci faktör, hesap bazlı anahtarla": anahtarı oturum sahibinin değiştirebilmesi ve kuralları eklenir.
  - "Oturum sahibinin kimliği": yanıtta ikinci adımın durumu bulunur.

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | `AccountController` yeni uç; `dto/TwoFactorUpdate`; `AccountView.twoFactorEnabled` + mapper; `AccountService.changeTwoFactor`; `Account.useTwoFactor`; `ApiErrorCode.TWO_FACTOR_EMAIL_NOT_VERIFIED`; `RateLimitPolicy.TWO_FACTOR_CHANGE`; `SecurityConfig`; `messages*.properties` (`mail.twoFactor*`, hata metni); yeni `TwoFactorToggleTest` (`critical`) |
| `web/` | Evet | `/account/two-factor` sayfası + `TwoFactorForm`; BFF `app/api/account/two-factor`; Hesabım Güvenlik bölümü; `messages/tr.json` + `en.json`; testler |
| DB | Hayır | `accounts.two_factor_enabled` zaten var (`003`) |
| `shared/` | Evet | `openapi.yaml` yeniden üretilir |
| `docs/` | Evet | ADR-103, ADR-104; ADR-017 ve ADR-025'e not; API referansı; `hesap-ve-oturum.md` Tarihçe ve bilinçli sınırlar; `00-current-state.md` (L13 kalkar) |

## Bağımlılık

Yeni paket yok (K7). Kod altyapısı (ADR-025) aynen kullanılır.

## Kişisel veri (K10)

Yeni kişisel veri alanı yok. Bildirim e-postası hesabın kendi adresine gider ve yalnızca değişikliği ve parola sıfırlama bağlantısını içerir. Parola log'a yazılmaz.

## Non-goals

- TOTP (doğrulayıcı uygulama), SMS, kurtarma kodları (ADR-025'te reddedildi; B4 açık).
- "Bu cihazı hatırla".
- Admin veya satıcı için ikinci adımı zorunlu kılmak.
- Açınca veya kapatınca diğer oturumları kapatmak. Gerekçe design.md D1'de.

## Riskler

- **E-postaya erişimi kaybeden kullanıcı:** ikinci adım açıksa giremez. Parola sıfırlama da aynı e-postaya gider; bugün de e-postayı kaybeden hesabını kurtaramaz. Destek süreci ayrı bir konudur.
- **Çalınmış oturum:** saldırgan ikinci adımı parola olmadan kapatamaz. Parolayı da biliyorsa kapatabilir; o zaman kullanıcıya bildirim e-postası gider.

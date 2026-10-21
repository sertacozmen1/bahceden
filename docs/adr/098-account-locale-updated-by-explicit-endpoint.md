# [ADR-098] Hesabın dili oturum sahibinin açık değerle çağırdığı bir uçla da değişir

**Tarih:** 2026-10-21
**Durum:** Accepted
**Kaynak:** `add-account-locale-update` design.md D1

---

## Context

Hesabın dili kayıtta yazılır ve her başarılı girişte isteğin diliyle güncellenir. E-postalar o dilde gider ([ADR-032](032-account-locale-stored-and-refreshed-on-login.md)). ADR-032 ayrı bir "dil tercihi" ucunu reddetmişti, çünkü o gün dil seçen bir ekran yoktu.

Başlıktaki dil menüsü artık var ([ADR-030](030-locale-from-cookie-then-header.md)). Oturum açıkken dil değiştiren kullanıcının e-postaları, bir sonraki girişe kadar eski dilde gidiyordu (`00-current-state.md` L19). Oturum 30 güne kadar sürer.

## Decision

- Yeni uç: `PUT /api/v1/accounts/me/locale`, kimlik doğrulamalı.
  - Gövde `AccountLocaleUpdate { locale }`, değerler `TR` / `EN` (`SupportedLocale`).
  - Başarıda `204`, gövdesiz.
- Hesap token'dan gelir. Uçta hesap kimliği yoktur, başkasının hesabı adreslenemez ([ADR-007](007-query-level-ownership-scoping.md) ile aynı ilke).
- `AccountService.changeLocale`, mevcut `Account.useLocale`'i kullanır. Dil aynıysa satır güncellenmez; uç idempotenttir.
- Hatalar:
  - token yok veya geçersiz: `401`;
  - hesap token'dan sonra silinmişse `401` (`AUTH_REQUIRED`), diğer `/accounts/me` uçlarıyla aynı;
  - alan eksik veya tanınmıyor: `400` (`VALIDATION_FAILED`).
- Hız sınırı yoktur. Uç ucuzdur, kimlik doğrulamalıdır ve e-posta ya da dış çağrı üretmez.
- Yanıt mesajları isteğin dilindedir ([ADR-031](031-api-messages-by-accept-language.md)). Hesabın yeni dili yalnızca e-postaları etkiler.

| Alternatif | Neden reddedildi |
|---|---|
| Gövdesiz uç, dil `Accept-Language`'den | Yanıt dilini taşıyan başlık ile yazılacak değer aynı olur. Başlık düzenindeki bir hata yanlış dili sessizce yazar. |
| Genel `PATCH /accounts/me` profil ucu | Bugün değişebilen tek alan dil. Genel uç, alan başına yetki ve doğrulama sorusunu bugünden açar (§3). |
| Her kimlik doğrulamalı istekte dili başlıktan güncellemek | Her okuma bir yazmaya dönüşür. |
| Serbest metin (`"en"`) ve sunucuda doğrulama | Tanınmayan değer varsayılana düşer (`SupportedLocale.fromTag`). Hata yerine hesap sessizce Türkçeye döner. |

## Consequences

### Olumlu
- Dil menüsünden yapılan seçim, girişi beklemeden e-postalara yansır.
- Dil listesi sözleşmeye enum olarak çıkar; `web` geçersiz değeri tip seviyesinde gönderemez.

### Olumsuz
- Sözleşmede yeni bir uç ve şema. Yeni bir dil, enum ve sözleşme değişikliği de gerektirir. Bu, dil eklemenin zaten gerektirdiği changeset'e eklenir.

### Nötr / Trade-off
- Girişte dil güncellemesi sürer (ADR-032). Hesabın dili, son seçimi veya son girişi izler.

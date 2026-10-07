# [ADR-032] Hesabın dili saklanır; kayıtta yazılır, girişte güncellenir; e-postalar o dilde

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-i18n` design.md D4 · proje sahibi "hesabın dili saklansın" seçeneğini onayladı (şema değişikliği)

---

## Context

E-postalar istek-yanıt döngüsünün dışında okunur. Doğrulama bağlantısının yeniden gönderilmesi ve giriş kodu gibi akışlarda isteğin dili, adresin sahibinin dili olmayabilir. "Hesabınız zaten var" bildirimi ise başkasının yaptığı bir kayıt denemesiyle tetiklenebilir; isteğin dilinde gönderilirse yabancının dilinde gider.

## Decision

- **Şema:** `accounts.locale VARCHAR(8) NOT NULL DEFAULT 'tr'`, `CHECK (locale IN ('tr','en'))`; changeset `005-account-locale.sql`.
  - Ekleyici değişiklik: mevcut hesaplar `tr` olur, veri kaybı yok.
  - `--rollback` kolonu ve kısıtı düşürür.
- **Ne zaman yazılır:**
  - kayıtta isteğin dili yazılır;
  - her başarılı parola adımında (`POST /api/v1/sessions`) isteğin diliyle güncellenir: kullanıcının en son kullandığı dil.
  - Hatalı parola ve kayıt denemesi hesabın dilini değiştirmez.
- **Hesabın dilinde gönderilenler:** doğrulama bağlantısı, giriş kodu ve "hesabınız zaten var" bildirimi.
- **Yanıt mesajları** (`202` gövdeleri, hatalar) her zaman isteğin dilindedir ([ADR-031](031-api-messages-by-accept-language.md)).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Hesapta saklamamak, isteğin dilini kullanmak | Proje sahibi saklamayı seçti. "Zaten var" bildirimi yabancının dilinde gider; yeniden gönderme isteğin diline bağlı kalır. |
| Ayrı "dil tercihini değiştir" ucu | Bugün ekranı yok; girişte güncelleme ihtiyacı karşılıyor. Ekran gelince ayrı karar. |
| Dili ikinci faktör adımında da güncellemek | Parola adımı her girişte zaten çalışıyor; tek güncelleme noktası yeterli. |

## Consequences

### Olumlu
- E-postalar alıcının dilinde; yabancı bir kayıt denemesi bildirimi değiştiremez.
- `api` istek bağlamı olmadan da (yeniden gönderme, ileride zamanlanmış işler) doğru dili bilir.

### Olumsuz
- Oturum açıkken dil değiştiren kullanıcının sonraki e-postası bir sonraki girişe kadar eski dilde gider.
- Yeni dil eklemek `CHECK` kısıtını genişleten bir changeset gerektirir.

### Nötr / Trade-off
- **KVKK:** dil tercihi tek başına kişiyi tanımlamaz. Hesapla birlikte yaşar ve silinir; log'a yazılmaz.

# [ADR-021] Kimlik yanıtları hesabın varlığını açığa çıkarmaz

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-account-and-session` design.md D4

---

## Context

Kayıt ve giriş uçları, bir e-posta adresinin sistemde kayıtlı olup olmadığını öğrenmenin en kolay yüzeyidir (hesap keşfi / enumeration). Farklı durum kodu, farklı mesaj veya belirgin bir süre farkı bu bilgiyi bedava verir. Hesap varlığı, hedefli oltalama ve kimlik bilgisi doldurma saldırılarının ilk adımıdır.

## Decision

- **Kayıt** her durumda `202` ve **birebir aynı** gövdeyi döner. Fark yalnızca gönderilen e-postanın içeriğindedir: yeni adrese doğrulama bağlantısı, kayıtlı adrese "hesabınız zaten var" bildirimi. Kayıtlı bir adresle yapılan ikinci kayıt mevcut hesabın parolasını **değiştirmez**.
- **Giriş** hatalarında tek bir kod ve mesaj döner (`AUTH_INVALID_CREDENTIALS`): hangi alanın yanlış olduğu söylenmez.
- Var olmayan hesapla girişte de bir BCrypt karşılaştırması yapılır (açılışta üretilen bir yem hash'e karşı); yanıt süresi hesabı ele vermez.
- [ADR-017](017-email-verification-non-blocking-and-2fa-switch.md) sonrası "doğrulanmamış hesap" diye bir giriş hatası yoktur; böyle bir kod tanımlanmaz.
- İkinci faktör adımında da bilinmeyen e-posta, açık girişim olmaması, hatalı ve süresi dolmuş kod aynı kodu alır (`AUTH_CODE_INVALID`).

| Alternatif | Neden reddedildi |
|---|---|
| Kayıtlı e-postada `409` | Doğrudan hesap keşfi. |
| "Bu e-posta kayıtlı değil" / "Parola yanlış" ayrımı | Aynı bilgiyi giriş ucundan sızdırır. |
| Doğrulanmamış hesapla girişte ayrı hata | ADR-017 sonrası doğrulama giriş koşulu değil; ayrıca bir varlık sinyali olurdu. |

## Consequences

### Olumlu
- Kayıt, giriş ve ikinci adım uçlarından hesap varlığı öğrenilemez.
- Kayıt ucu hesap ele geçirme yolu değildir; bu davranış testle sabitlendi.

### Olumsuz
- Kullanıcı yanlış e-postayla kayıt olmaya çalıştığını ekranda değil, e-posta kutusunda öğrenir.
- Var olmayan hesapla giriş de bir BCrypt maliyeti öder (kasıtlı).

### Nötr / Trade-off
- Hız sınırlaması ([ADR-022](022-in-memory-fixed-window-rate-limiting.md)) bu kuralın tamamlayıcısıdır: yanıt aynı olsa bile sınırsız deneme kaba kuvvete kapı açardı.

# [ADR-025] İkinci faktör kodu hesaba bağlıdır; ikinci adım e-posta + kod alır

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-account-and-session` design.md D8

---

## Context

[ADR-017](017-email-verification-non-blocking-and-2fa-switch.md) e-posta ile ikinci faktörü hesap bazlı bir anahtara (`two_factor_enabled`) bağladı ve açık yolunun da uygulanmasını şart koştu. Uygulama sırasında `003` changeset'inin `login_challenges` tablosunda bir açık görüldü: kodu bağlayan tek kolon `code_hash` ve bu kolon tekil.

- Kod tek başına hash'iyle aranırsa, rastgele bir 6 haneli kod **başka bir hesabın** açık girişimini tutabilir ve o hesabın oturumunu açar.
- 6 haneli kodda (10⁶ olasılık) iki girişimin aynı kodu alması tekil kısıtı ihlal eder ve isteği `500` ile düşürür.

`003` uygulanmış bir changeset'tir ve düzenlenemez.

## Decision

Kullanıcı şemanın değişmemesini seçti. Buna göre:

- Saklanan değer `SHA-256(hesapKimliği + ":" + kod)`'dur. Kod tek başına aranmaz.
- **Birinci adım** (`POST /sessions`, anahtar açık hesapta) token döndürmez; `202` döner ve e-postaya 6 haneli kod gönderir. Kod **10 dakika** geçerlidir.
- **İkinci adım** (`POST /sessions/challenge`) **e-posta + kod** alır. Sunucu o hesabın **en son** açılan, süresi geçmemiş ve kullanılmamış girişimini bulur ve kodu onunla karşılaştırır (sabit süreli). Yalnızca son kod geçerlidir.
- Girişim başına **5 hatalı deneme**; sınır dolunca doğru kod bile `429` alır. Hatalı deneme sayısı istisnaya rağmen kalıcıdır.
- Girişim satırı kilitlenerek okunur: eşzamanlı denemeler sayacı kaybetmez, doğru kod iki oturum açamaz.
- Aynı hesapta aynı hash'in tekrar üretilmesi (≈ 10⁻⁶) üretim anında denetlenir ve yeni kod çekilir.
- Anahtarı değiştiren uç veya ekran yoktur; dev seed'de anahtarı açık bir hesap (`iki-adim@ornek.test`) bulunur.

| Alternatif | Neden reddedildi |
|---|---|
| Girişimi opak bir `challengeToken` ile tanımlamak | En temiz çözüm, ama yeni bir changeset gerektiriyordu (`challenge_token_hash` kolonu, `code_hash` tekilliğinin kaldırılması). Kullanıcı şemanın değişmemesini seçti. |
| Kodu tek başına aramak | Rastgele kod başka bir hesabın girişimini tutabilir: hesap ele geçirme. |
| TOTP (doğrulayıcı uygulama) | Yeni paket (QR, gizli anahtar yönetimi) ve ayrı bir karar; e-posta altyapısı zaten var. |

## Consequences

### Olumlu
- Şema değişmedi; kod yalnızca ait olduğu hesabın girişimine karşı doğrulanır.
- Tekil kısıt çakışması 500 üretmez.

### Olumsuz
- Aynı hesapta iki sekmede giriş başlatılırsa yalnızca son kod geçerlidir; ilk sekmedeki kod reddedilir.
- Ürün yüzeyinde kapalı ama bakımı yapılan bir akış taşınıyor (ADR-017'nin kabul ettiği maliyet).

### Nötr / Trade-off
- 6 haneli kodun SHA-256'sı kaba kuvvete dayanıksızdır. Koruma hash'te değil kısa ömür, deneme sınırı ve hız sınırındadır ([ADR-022](022-in-memory-fixed-window-rate-limiting.md)).

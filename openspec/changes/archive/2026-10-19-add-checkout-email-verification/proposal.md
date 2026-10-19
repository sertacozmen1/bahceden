# add-checkout-email-verification

**Durum:** Onaylandı (2026-10-19, proje sahibi), uygulandı ve kullanıcı onayıyla archive edildi (2026-10-19).

> Bu change [`00-current-state.md`](../../../../docs/reference/00-current-state.md) L11 sınırlamasının sipariş tarafını kapatır. Karar proje sahibince verildi (2026-10-17): e-postasını doğrulamamış alıcı sipariş veremez; satıcı başvurusundaki kapının ([ADR-043](../../../../docs/adr/043-seller-application-requires-verified-email.md)) aynısı. `KALANLAR.md`'deki hiçbir maddeye bağlı değildir.

## Why

Hesap doğrudan etkin açılır ve e-posta doğrulaması girişi engellemez ([ADR-017](../../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md)). Bugün doğrulanmamış bir adresle sipariş verilebiliyor. Sipariş, kargo, teslim ve ayıplı ürün talebi e-postaları bu adrese gidiyor; adres yanlışsa alıcı ödediği siparişin hiçbir haberini almaz. Teslim e-postasındaki 48 saatlik sorun bildirimi süresi ([ADR-091](../../../../docs/adr/091-delivered-email-to-buyer-with-claim-deadline.md)) gibi süreli bilgiler de kaybolur.

Satıcı başvurusu aynı sorunu doğrulama kapısıyla çözdü. Para ödenen ilk adım sipariş; kapı orada en az sürtünmeyle en çok güvenceyi verir.

## What Changes

- **`api`:**
  - `POST /api/v1/orders`: hesabın e-postası doğrulanmamışsa **422** `ORDER_EMAIL_NOT_VERIFIED` döner ve sipariş oluşmaz.
  - Aynı `Idempotency-Key` ile tekrar, kapıdan **önce** değerlendirilir: önceden oluşmuş sipariş yine döner.
  - Sepet, adresler, ödemenin tamamlanması (`POST /orders/{n}/payment`) ve sipariş görüntüleme kapısızdır. Giriş yine engellenmez (ADR-017).
  - Mesaj metni `messages*.properties`'e eklenir; sözleşmede 422 açıklamasına yeni kod yazılır.
- **`web`:**
  - Ödeme sayfası (`/checkout`), e-postası doğrulanmamış alıcıya formun yerine "önce e-postanızı doğrulayın" panelini ve mevcut "yeni doğrulama bağlantısı gönder" düğmesini gösterir. Sipariş özeti ve toplamlar görünmeye devam eder.
  - Sepet sayfası değişmez; "Siparişe geç" ödeme sayfasına gider ve kapı orada anlatılır.
  - Ödeme formu, kapının yarışla atlandığı durumda (sayfa açıkken başka bir sekmede durum değişmez ama sunucu reddederse) sunucunun mesajını formun üstünde gösterir; mevcut genel hata eşlemesi yeterli.
- **Geliştirme verisi:** yeni dev changeset'i `015-dev-seed-verified-buyer.sql`, `alici@ornek.test`'i doğrulanmış işaretler (D3). Kapıyı denemek için yeni hesap açılır.
- **Sözleşme:** yeni uç veya alan yok. `ApiError.code` için yeni bir değer ve `placeOrder`'ın 422 açıklaması değişir; `openapi.yaml` yeniden üretilir.

## Capabilities

- **Değişen:** `cart-and-checkout`. "Sipariş sepetten oluşturulur…" gereksinimine e-posta doğrulaması koşulu ve üç senaryo eklenir (delta: `specs/cart-and-checkout/spec.md`).

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | `CheckoutService.placeOrder` (kapı); `ApiErrorCode.ORDER_EMAIL_NOT_VERIFIED`; `OrderController` 422 açıklaması; `messages*.properties` (`error.orderEmailNotVerified`); `015-dev-seed-verified-buyer.sql` + master changelog; `critical` testler |
| `web/` | Evet | `(shop)/checkout/page.tsx` doğrulama paneli; `messages/*.json` (`checkout.verifyFirst.*`); sayfa testi |
| DB | Evet (yalnızca dev verisi) | `contextFilter:dev` changeset'i, idempotent `update`; `--rollback` önceki değeri geri yazar. Şema değişmez |
| `shared/` | Evet (açıklama) | `placeOrder` 422 açıklaması |
| `docs/` | Evet | ADR-093…095; ADR-017 ve ADR-043'e not; `02-api-reference.md`; `sepet-ve-siparis.md`, `hesap-ve-oturum.md` (bilinçli sınırlar + Tarihçe); `docs/how-to/yerel-gelistirme.md` (dev hesapları tablosu); `00-current-state.md` (L11 güncellenir, sayılar, sıradaki adım) |

## Bağımlılık

Yeni paket yok (K7).

## Kişisel veri (K10)

Yeni kişisel veri alanı yok. `accounts.email_verified` mevcut bir alandır; yalnızca okunur. Dev changeset'i sahte hesaba dokunur.

## Non-goals

- Girişi doğrulamaya bağlamak (ADR-017 geçerli).
- Sepete eklemeyi ya da adres kaydını doğrulamaya bağlamak.
- Doğrulama bağlantısından sonra kullanıcıyı ödeme sayfasına geri götürmek. Doğrulama sayfasının dönüş adresi bugün yok; alıcı ödeme sekmesini yenileyerek devam eder. İhtiyaç görülürse ayrı iş.
- Kapıdan önce açılmış, ödemesi bekleyen siparişleri etkilemek. Ödeme tamamlama kapısızdır.
- `SELLER_EMAIL_NOT_VERIFIED`'ı genel bir koda çevirmek: sözleşmede kırıcı değişiklik (§9).

## Riskler

- **Sürtünme:** yeni alıcı ilk siparişinden önce gelen kutusuna gitmek zorunda. Kayıtta doğrulama bağlantısı zaten gönderiliyor; ödeme sayfasındaki düğme yenisini gönderir.
- **E-posta gecikmesi veya spam klasörü:** alıcı siparişi tamamlayamaz. Mesaj spam klasörüne bakmayı ve yeni bağlantı istemeyi söyler.

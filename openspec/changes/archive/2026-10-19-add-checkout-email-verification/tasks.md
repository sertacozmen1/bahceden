# Tasks: add-checkout-email-verification

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7). Şema değişmez; yalnızca bir dev veri changeset'i eklenir.

## Faz 1 · `api`: sipariş kapısı ve geliştirme verisi

- [x] 1.1 `ApiErrorCode.ORDER_EMAIL_NOT_VERIFIED` (422); `messages.properties` + `messages_en.properties`: `error.orderEmailNotVerified`. `MessagesCatalogTest` yeşil.
- [x] 1.2 `CheckoutService.placeOrder`: tekrar anahtarı kontrolünden sonra, adres ve sepetten önce hesabın `email_verified` değerini güncel kayıttan okur (D1).
- [x] 1.3 `OrderController.place` 422 açıklamasına yeni kod; `openapi.yaml` yeniden üretilir.
- [x] 1.4 `015-dev-seed-verified-buyer.sql` (`contextFilter:dev`, idempotent, `--rollback`) + master changelog (D3). Dev bağlamı izolasyon testi yeşil.
- [x] 1.5 **Test (`critical`):**
  - doğrulanmamış alıcı: 422 `ORDER_EMAIL_NOT_VERIFIED`, sipariş ve ödeme kaydı oluşmaz, sağlayıcı çağrılmaz;
  - doğrulanmış alıcı: sipariş oluşur (mevcut testler);
  - doğrulama sonrası aynı token'la sipariş oluşur;
  - aynı anahtarla tekrar, hesap sonradan doğrulanmamış görünse de ilk siparişi döner;
  - sepet, adres ve ödeme tamamlama doğrulanmamış hesapta çalışır.
- [x] 1.6 **Doğrulama:** `./mvnw -B verify` temiz (327 test, 0 hata; 3 yeni `CheckoutTest`, 1 yeni `DevSeedIsolationTest` testi; `openapi.yaml`'da yalnızca `placeOrder` 422 açıklaması değişti).

## Faz 2 · `web`: ödeme sayfasında doğrulama paneli

- [x] 2.1 `npm run gen:api`; `(shop)/checkout/page.tsx`: `account.emailVerified` yanlışsa `CheckoutForm` yerine doğrulama paneli (`ResendVerificationButton`); özet ve toplamlar kalır (D2).
- [x] 2.2 Metinler `checkout.verifyFirst.*` (`tr.json` + `en.json`).
- [x] 2.3 **Test:** sayfa doğrulanmamış hesapta paneli ve düğmeyi gösterir, formu göstermez; doğrulanmış hesapta form görünür; anahtar eşitliği testi yeşil.
- [x] 2.4 **Doğrulama:** `lint`, `typecheck`, `test` (277 test, 70 dosya; 2 yeni `checkout/page.test.tsx`), `build` temiz.

## Faz 3 · Uçtan uca doğrulama ve doküman

- [x] 3.1 `docker compose up -d --build`: yeni hesap → sepete ürün → ödeme sayfasında panel → yeni bağlantı mailpit'te → doğrula → sayfayı yenile → form → sipariş. `alici@ornek.test` doğrulanmış açılır ve sipariş verir. (Yeni hesapla `api` üzerinden: 422 `ORDER_EMAIL_NOT_VERIFIED` → mailpit bağlantısıyla doğrulama 204 → aynı token'la sipariş 201. Tarayıcıda ikinci bir doğrulanmamış hesapla panel ve "Yeni doğrulama bağlantısı gönder" denendi. `015` seed'i dev'de uygulandı, `alici@ornek.test` doğrulanmış.)
- [x] 3.2 Panelin 375 px koyu ve 1440 px açık temada ekran görüntüsü; yatay taşma yok, düğme 44 px; konsol temiz.
- [x] 3.3 ADR-093…095 + `docs/adr/index.md` + `docs/.tools/mkdocs.yml`; ADR-017 ve ADR-043'e not.
- [x] 3.4 `docs/reference/02-api-reference.md`; `docs/features/sepet-ve-siparis.md`, `hesap-ve-oturum.md` (bilinçli sınırlar + Tarihçe), `docs/features/index.md`; `docs/how-to/yerel-gelistirme.md` (dev hesapları); `00-current-state.md` (L11 kaldırıldı: ticari eylemlerin ikisi de doğrulama istiyor, hesap açma ve giriş ADR-017 gereği bilinçli; sayılar, sıradaki adım). `./docs/.tools/serve.sh --build` temiz. Ek: `sepet-ve-siparis.md` bilinçli sınırlarındaki eskimiş "kargo yok" ve "ayıplı ürün yok" maddeleri düzeltildi; ADR-017'nin üst bilgisine not eklendi.
- [x] 3.5 Delta spec ana spec'e sync edilir.
- [x] 3.6 Archive kullanıcı onayıyla yapıldı (2026-10-19).

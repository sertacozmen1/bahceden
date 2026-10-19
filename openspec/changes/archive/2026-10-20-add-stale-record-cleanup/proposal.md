# add-stale-record-cleanup

**Durum:** Onaylandı (2026-10-19, proje sahibi), uygulandı ve kullanıcı onayıyla archive edildi (2026-10-20).

> Bu change [`00-current-state.md`](../../../../docs/reference/00-current-state.md) L32 (ödenmeyen siparişler birikir) ve L15 (süresi dolmuş token satırları birikir) sınırlamalarını kapatır. `KALANLAR.md`'deki hiçbir maddeye bağlı değildir. Proje sahibi 2026-10-19'da bu işi seçti.

## Why

**Ödenmeyen siparişler.** Alıcı ödeme sayfasından dönmezse sipariş `PENDING_PAYMENT` durumunda kalıyor. Ödeme dönüşü süresi geçmiş siparişi sağlayıcıdan doğrulayıp kapatıyor ([ADR-066](../../../../docs/adr/066-payment-redirect-verify-lock-stock-on-success.md)), ama dönüş hiç gelmezse kimse kapatmıyor. Alıcının "Siparişlerim" listesinde süresiz "Ödeme bekleniyor" satırları birikiyor (satıcı paneli bu durumdaki siparişleri zaten göstermiyor). Daha önemlisi, sağlayıcıda geç tamamlanmış bir ödeme varsa iade edilmiyor.

**Token satırları.** Doğrulama, sıfırlama, giriş kodu ve yenileme token'ları süreleri dolduktan sonra da tabloda kalıyor. Her biri bir hesaba bağlı ve işe yaramayan bir kayıt. Veri en aza indirme (KVKK) açısından gereksiz saklama; tablolar da sınırsız büyüyor.

## What Changes

- **`api` · ödenmeyen sipariş işi (`order`):**
  - Zamanlanmış iş, ödeme süresi (`payment_expires_at`) bir tolerans süresi kadar önce dolmuş `PENDING_PAYMENT` siparişleri sırayla alır.
  - Her sipariş kendi transaction'ında kilitlenir ve durumu yeniden okunur. Hâlâ ödeme bekliyorsa ödeme dönüşüyle **aynı** kural uygulanır:
    - sağlayıcı ödemeyi başarılı bildirirse ödemenin tamamı `PAYMENT_EXPIRED` nedeniyle iade edilir;
    - başarısız bildirirse sipariş `PAYMENT_FAILED` olur;
    - bekliyorsa sipariş `PAYMENT_EXPIRED` olur.
  - Durum geçmişine yapan taraf "sistem" olarak yazılır.
  - Sağlayıcı hatası o siparişi değiştirmez; sonraki turda yeniden denenir, diğer siparişler etkilenmez.
  - Stok ve sepet değişmez (ödenmemiş sipariş zaten stok düşürmemişti). E-posta gitmez.
- **`api` · süresi geçmiş token temizliği (`account`):**
  - Zamanlanmış iş, süresi saklama payı kadar önce dolmuş satırları siler:
    - e-posta doğrulama ve parola sıfırlama token'ları;
    - giriş kodları (`login_challenges`);
    - yenileme token'ları: yalnızca **ailesinin bütün token'larının** süresi dolmuşsa. Kullanılmış eski bir token'ın tekrar sunulması aileyi iptal eder ([ADR-018](../../../../docs/adr/018-refresh-token-rotation-with-family-revocation.md)); canlı bir ailenin eski token'ı silinirse bu koruma kaybolur.
  - Silinen satır sayıları loglanır; token değeri ve hesap e-postası loglanmaz.
- **Yapılandırma:** yeni `bahceden.cleanup.*`: iş açık mı, tur aralığı, ödenmemiş sipariş toleransı ve tur başına sayısı, token saklama payı. Testlerde iş kapalıdır ve doğrudan çağrılır.
- **`web`:** değişmez. Kapatılan siparişler alıcının listesinde mevcut durum etiketleriyle görünür ("Süresi doldu", "Ödeme başarısız").
- **Sözleşme:** değişmez.

## Capabilities

- **Değişen:** `cart-and-checkout`. "Ödeme sağlayıcıdan doğrulanır; stok ödeme başarılı olunca düşer" gereksinimine dönüşü gelmeyen siparişlerin işle kapatılması eklenir.
- **Değişen:** `account-identity`. Yeni gereksinim "Süresi geçmiş kimlik kayıtları temizlenir".

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | `order`: `PendingPaymentSweep` + zamanlaması; `CheckoutService` içinde ödeme dönüşüyle ortak kapatma yolu (yapan taraf sistem); `OrderRepository` (süresi geçmiş kimlikler, kimlikle kilit). `account`: `ExpiredTokenCleanup` + zamanlaması; dört repository'de toplu silme sorgusu. `common`: `BahcedenProperties.Cleanup`; `application.yaml`, `application-test.yaml`, `api/.env.example` notu; `critical` testler |
| `web/` | Hayır | - |
| DB | Evet (ekleyici indeks) | `016-pending-payment-index.sql`: `orders (payment_expires_at, id) where status = 'PENDING_PAYMENT'` kısmi indeksi; `--rollback` indeksi düşürür. Sorgu planı indekssiz tam tarama gösterdi (Faz 1). İş satır siler (yalnızca token tabloları) ve sipariş durumunu günceller |
| `shared/` | Hayır | - |
| `docs/` | Evet | ADR-096, ADR-097; `01-domain-model.md` (silme davranışı); `sepet-ve-siparis.md`, `hesap-ve-oturum.md` (Tarihçe, sınırlar); `docs/how-to/yerel-gelistirme.md` (yapılandırma notu); `00-current-state.md` (L15, L32 kalkar; yeni tek örnek sınırı L38'e eklenir) |

## Bağımlılık

Yeni paket yok (K7). Spring'in kendi zamanlayıcısı kullanılır; gönderi sorgusu ve talep süre işiyle aynı desen.

## Kişisel veri (K10)

Kişisel veri azalır: token satırları hesaba bağlıdır ve süreleri dolunca işe yaramaz. Siparişler silinmez (yasal kayıt, `AGENTS.md` §11.4); yalnızca durumları kapanır. Log'a sayılar ve sipariş numarası yazılır.

## Non-goals

- Ödenmemiş siparişleri silmek. Siparişler silinmez; kapatılır.
- Sağlayıcıda ödeme oturumunu iptal etmek. Sağlayıcı arayüzünde böyle bir işlem yok; gerçek sağlayıcı seçimine bağlı (KALANLAR A1).
- Sağlayıcı bildirimi (webhook) ile ödeme sonucu almak (A1).
- Sepet, ürün fotoğrafı, belge, etiket ve talep fotoğrafı temizliği. Saklama süreleri hukuk kararı bekliyor (KALANLAR A5).
- Kapatılan sipariş için alıcıya e-posta.
- Çok örnekli çalışma için dağıtık kilit (§3). Satır kilidi çift işlemi önler.

## Riskler

- **Geç tamamlanan ödeme:** sağlayıcıda ödeme toleranstan da sonra tamamlanırsa sipariş zaten kapanmış olur ve iade bu iş tarafından yapılmaz. Tolerans, gerçek sağlayıcının ödeme oturumu süresinden uzun seçilmeli (A1'de doğrulanacak). Sahte sağlayıcıda oluşmaz.
- **İş hatası:** sağlayıcıya ulaşılamazsa siparişler bekler; bir sonraki turda yeniden denenir.

# Design: add-stale-record-cleanup

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2): D1 → ADR-096, D2 → ADR-097.
> Çerçeve: [ADR-066](../../../../docs/adr/066-payment-redirect-verify-lock-stock-on-success.md) (ödeme dönüşü sağlayıcıdan doğrular, süresi geçeni kapatır, geç alınmış ödemeyi iade eder), [ADR-018](../../../../docs/adr/018-refresh-token-rotation-with-family-revocation.md) (yenileme token ailesi ve tekrar kullanımda iptal), zamanlanmış iş deseni [ADR-075](../../../../docs/adr/075-shipment-status-by-scheduled-polling.md) ve [ADR-083](../../../../docs/adr/083-defect-claim-deadlines-by-scheduled-sweep.md). Hiçbir ADR'ye aykırı değildir.

## Kararlar

### D1 · Ödenmeyen siparişler zamanlanmış işle, ödeme dönüşüyle aynı kuralla kapatılır

- **Seçim:** `status = PENDING_PAYMENT` ve `payment_expires_at < şimdi − bahceden.cleanup.pending-payment-grace` (varsayılan 1 saat). En eski süreliden başlayarak tur başına `bahceden.cleanup.pending-payment-batch-size` (100) sipariş.
- **İşleme:** her sipariş kendi transaction'ında `PESSIMISTIC_WRITE` ile kilitlenir ve durumu yeniden okunur. Hâlâ `PENDING_PAYMENT` ise `CheckoutService`'in ödeme dönüşünde kullandığı kapatma yolu çalışır. Süre dolduğu için sonuç şunlardan biridir:

  | Sağlayıcı | Sonuç |
  |---|---|
  | `SUCCEEDED` | Ödemenin tamamı `PAYMENT_EXPIRED` nedeniyle iade edilir; sipariş `PAYMENT_EXPIRED` |
  | `FAILED` | `PAYMENT_FAILED` |
  | `PENDING` | `PAYMENT_EXPIRED` |

  Satıcı siparişleri `VOID` geçişiyle kapanır. Geçmişe yapan taraf sistemdir (`actor_account_id` boş), gönderi sorgusundaki gibi.
- **Hata yalıtımı:** sağlayıcı hatası o siparişin transaction'ını geri alır; sipariş değişmez ve sonraki turda yeniden denenir. Diğer siparişler etkilenmez.
- **Kod:** `settle` mantığı ikiye bölünmez. Yapan tarafı parametre alan tek bir yol hem ödeme dönüşünde (alıcı) hem işte (sistem) kullanılır (K9).
- **Tolerans neden var:** alıcı sağlayıcıdan dönerken ödeme dönüşü siparişi zaten kapatır. Tolerans, dönüşün öncelikli olmasını ve sağlayıcıda geç tamamlanan bir ödemenin de iş tarafından görülüp iade edilmesini sağlar.

**Gerekçe:**
- Ödeme dönüşü ile iş aynı sonucu üretmeli; iki ayrı kural ayrışır.
- Satır kilidi ve durum yeniden okuması, dönüş ile işin aynı anda çalıştığı durumda çift kapatmayı ve çift iadeyi önler.
- Sipariş silinmez: numarası alıcıya gösterilmiş olabilir, sağlayıcıda bir kaydı vardır (`AGENTS.md` §11.4).

**Reddedilen alternatifler:**
- (a) Ödenmemiş siparişleri silmek: yasal ve mali kayıt kaybı; sağlayıcı kaydıyla eşleşme kopar.
- (b) Sağlayıcıya sormadan `PAYMENT_EXPIRED` yazmak: geç tamamlanmış bir ödeme iadesiz kalır.
- (c) Listelerde süresi geçmiş siparişleri gizlemek: kaydı değiştirmez, sorunu ekrana taşır.
- (d) Sağlayıcı bildirimi (webhook): gerçek sağlayıcıya bağlı (KALANLAR A1).

### D2 · Süresi geçmiş token satırları saklama payından sonra silinir; yenileme token'ları aile bütünüyle

- **Silinenler**, `bahceden.cleanup.token-retention` (varsayılan 7 gün) kadar önce süresi dolmuş satırlar:
  - `email_verification_tokens`, `password_reset_tokens`, `login_challenges`: `expires_at < kesim`;
  - `refresh_tokens`: `family_id`'si için `max(expires_at) < kesim` olan ailelerin bütün satırları.
- **Silme** toplu `delete` sorgusuyla, her tablo için ayrı transaction'da yapılır. Satır sayıları tek bir log satırında yazılır.
- **Neden yenileme token'ları aileyle:** kullanılmış bir token tekrar sunulursa, süresi geçmiş olsa bile ailenin tamamı iptal edilir (`SessionService.refresh`, ADR-018). Canlı bir ailenin eski token'ı silinirse çalınmış eski token "bilinmeyen token" olur ve aile iptal edilmez. Ailenin son token'ının süresi dolduktan sonra ailede yenilenebilecek bir şey kalmaz; silmek korumayı zayıflatmaz.
- **Saklama payı neden var:** destek ve olay incelemesi için kısa bir iz bırakır ("bağlantım çalışmıyor" şikâyetinde süresinin dolduğu görülebilir); sonra veri en aza indirilir.
- Aynı iş ve zamanlama ödenmemiş sipariş işiyle ortak yapılandırmayı paylaşır (`bahceden.cleanup.enabled`, `interval`), ama iki iş kendi paketindedir (`order`, `account`) ve birbirini çağırmaz.

**Gerekçe:**
- Süresi dolmuş token hiçbir akışta kabul edilmez; satırın tek değeri kısa süreli izdir.
- Toplu `delete` MVP ölçeğinde yeterlidir; satır satır yükleme gereksiz.
- `common` paketi alan paketlerine bağımlı olamaz (§3); her iş kendi paketinde, zamanlama da yanında durur.

**Reddedilen alternatifler:**
- (a) Yenileme token'larını tek tek süresine göre silmek: tekrar kullanım korumasını zayıflatır (yukarıda).
- (b) Süre dolar dolmaz silmek: destek izi kalmaz.
- (c) Veritabanı tarafında `pg_cron` veya bölümlü tablolar: yeni eklenti ve işletme yükü (K7, §3).
- (d) Hiç silmemek: KVKK veri en aza indirme ilkesine ve tablo büyümesine aykırı.

## Yapılandırma

| Anahtar | Varsayılan | Test |
|---|---|---|
| `bahceden.cleanup.enabled` | `true` | `false` (iş doğrudan çağrılır) |
| `bahceden.cleanup.interval` | `PT10M` | - |
| `bahceden.cleanup.pending-payment-grace` | `PT1H` | - |
| `bahceden.cleanup.pending-payment-batch-size` | `100` | - |
| `bahceden.cleanup.token-retention` | `P7D` | - |

Süreler pozitif, tur sayısı pozitif olmalıdır; değilse uygulama açılmaz (`@Validated`).

## Yetkilendirme ve kişisel veri

- Yeni uç yok.
- İş yalnızca token tablolarından satır siler; hesaplar, siparişler ve iadeler silinmez.
- Log: silinen satır sayıları, kapatılan siparişin numarası ve sonucu. Token, e-posta ve adres yazılmaz.

## Risks / Trade-offs

- **Geç tamamlanan ödeme toleranstan sonra:** sipariş kapanmış olur ve iş iade yapmaz. Tolerans gerçek sağlayıcının oturum süresine göre ayarlanmalı (A1). Ödeme dönüşü de kapanmış siparişte yeniden sağlayıcıya gitmez; bu bugünkü davranıştır.
- **Tek örnek:** iş her örnekte çalışır; satır kilidi sipariş tarafında çift işlemi, toplu silme token tarafında zararsız tekrarı önler. Mevcut L33 ve L38 ile aynı sınır.
- **Arama sorgusu:** sorgu planı indekssiz bütün `orders` tablosunu tarıyordu ve iş her turda çalışır. `016` changeset'i yalnızca ödeme bekleyen satırları kapsayan kısmi bir indeks ekler (`payment_expires_at, id`); iş satırları kapattıkça indeks küçük kalır.

## Migration Plan

Şema yalnızca ekleyici bir kısmi indeksle (`016`) değişir. İş ilk açılışta biriken eski satırları ve siparişleri tur tur temizler; tur başına sınır yükü yayar. Geri almak için `bahceden.cleanup.enabled=false`.

## Open Questions

Yok.

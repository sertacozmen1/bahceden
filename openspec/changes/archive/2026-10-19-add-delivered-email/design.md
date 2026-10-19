# Design: add-delivered-email

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2): D1 → ADR-091, D2 → ADR-092.
> Çerçeve [ADR-078](../../../../docs/adr/078-shipping-notifications-email-only-three-events.md) (kargo bildirimleri yalnızca e-posta; "teslim edildi" ayıplı ürün süresiyle eklenecek) ve [ADR-081](../../../../docs/adr/081-defect-claim-opening-window-lines-photos.md)'dir (talep penceresi teslimden itibaren `bahceden.claims.window`, 48 saat). ADR-078'in kararı çiğnenmez, öngördüğü dördüncü olay eklenir; ADR-078'e bu ADR'ye işaret eden bir not düşülür.

## Kararlar

### D1 · "Teslim edildi" e-postası: yalnızca alıcıya, geçiş uygulandığında, talep süresiyle

- **Tetikleyici:** `ShipmentProgressService.delivered`, satıcı siparişi `DELIVERED`'a gerçekten geçtiğinde `OrderNotifier.sellerOrderDelivered`'ı çağırır. Bu, "kargoya verildi" (`shipped`) ve "teslim edilemedi" (`deliveryFailed`) ile aynı yer ve desendir. Geçiş izinli değilse (zaten teslim edilmiş, iptal edilmiş) e-posta gitmez.
- **Alıcı:** yalnızca siparişin alıcısı, hesabının dilinde ([ADR-032](../../../../docs/adr/032-account-locale-stored-and-refreshed-on-login.md)).
- **Zaman:** e-posta transaction başarıyla bittikten sonra gönderilir (`sendAfterCommit`). Gönderim hatası teslim geçişini etkilemez, yalnızca loglanır ([ADR-019](../../../../docs/adr/019-email-via-smtp-provider-agnostic.md)).
- **İçerik:**
  - sipariş numarası;
  - üreticinin görünen adı;
  - talep penceresinin son anı (D2) ve pencerenin saat olarak uzunluğu;
  - sipariş detayı bağlantısı (`/account/orders/{orderNumber}`).
- **Son anın kaynağı:** geçişin zamanı (`now`) + `bahceden.claims.window`. Gönderi sorgusu aynı `now`'ı `shipments.delivered_at`'e yazar ve sipariş detayındaki `claimableUntil` da aynı toplamla hesaplanır (`OrderMapper.claimableUntil`). E-postadaki an ile ekrandaki an aynıdır.

**Gerekçe:**
- Talep penceresi alıcının tek süreli hakkı. Siteye girmeyen alıcının haberdar olması gerekir; ADR-078 bunu bilerek bu ana bırakmıştı.
- Satıcı durumu panelinde görür ve teslim onun için bir eylem gerektirmez. E-posta gürültü olur.
- Mevcut bildirim deseni (geçiş başarılıysa bildir, commit sonrası gönder) yinelenen bildirimde çift e-postayı zaten engeller. Yeni bir mekanizma gerekmez (K9).

**Reddedilen alternatifler:**
- (a) Satıcıya da e-posta: eylem gerektirmeyen bilgi; satıcı paneli yeterli.
- (b) E-postayı gönderi paketinde (`shipping`) üretmek: sipariş e-postaları `OrderNotifier`'da toplanmış; ikinci bir yer, metin ve dil mantığını ikiye böler.
- (c) Son anı e-postada hiç vermemek, yalnızca "sipariş sayfasından sorun bildirebilirsiniz" demek: L41'in asıl amacı süreyi söylemek.
- (d) Süre bitmeden hatırlatma e-postası da göndermek: zamanlanmış ek iş; ihtiyaç ölçülmeden eklenmez (§3).

### D2 · Son an Türkiye saatiyle, hesabın dilinde biçimlenir; biçim tek yardımcıda

- Son an e-postada iki biçimde geçer: tarih ve saat ("19 Ekim 2026 14:30", İngilizcede yerel biçim) ve saat olarak süre ("48 saat").
- Tarih-saat `Europe/Istanbul` saat diliminde gösterilir; metin bunu açıkça söyler ("Türkiye saatiyle"). Saat dilimi sabiti `BusinessDays.TURKEY`'den gelir. Pazaryeri tek bölgedir ([ADR-063](../../../../docs/adr/063-delivery-provinces-config-address-book.md)), alıcının saat dilimi hesapta tutulmaz.
- Biçim, yeni ve küçük bir yardımcıda toplanır: `common/time/DateTimeFormat.format(Instant, SupportedLocale)`. Kalıp sabittir, `d MMMM yyyy HH:mm`; ay adı hesabın dilinde tam yazılır ("19 Ekim 2026 14:30", "19 October 2026 14:30"). `MoneyFormat` ile aynı desen: bileşen veya metin dosyası elle biçim kurmaz.

**Gerekçe:**
- Mevcut e-postalar yalnızca göreli süre veriyor ("48 saat içinde", "3 iş günü içinde"). Bunlar bir eylemin hemen ardından gidiyor. "Teslim edildi" e-postası ise sorgu aralığı kadar gecikebilir ve saatler sonra okunabilir; göreli süre tek başına yanıltır. Kesin an, sipariş detayındaki geri sayımla aynı bilgiyi verir.
- Saat diliminin açıkça yazılması, yurt dışından bakan alıcı için belirsizliği kaldırır.
- `api` içinde tarih-saat biçimleyen kod yok (aranan: `DateTimeFormatter`, `ZoneId`); ikinci bir kullanım ortaya çıkınca dağılmasın diye tek yerde başlar. "Mevcut X neden yetmedi": `MoneyFormat` paradır, `BusinessDays` iş günü hesabıdır; ikisi de biçim üretmez.

**Reddedilen alternatifler:**
- (a) Yalnızca göreli süre ("48 saat içinde"): gecikmeli okunan e-postada yanlış anlaşılır.
- (b) UTC ile göstermek: alıcının kafasında bir dönüşüm daha gerektirir.
- (c) Biçimi `messages.properties`'te ICU `{0,date,...}` kalıbıyla yapmak: `MessageFormat`'ın tarih desteği `java.util.Date` ve JVM varsayılan saat dilimiyle çalışır; saat dilimi denetimi kaybolur.
- (d) Biçimi `OrderNotifier` içinde özel bir metotta yapmak: ikinci e-posta türü geldiğinde kopyalanır.
- (e) `java.time`'ın yerel biçim stilleri (`FormatStyle.MEDIUM` / `SHORT`): çıktı JDK'nın CLDR sürümüyle değişir (kısaltılmış ay, İngilizcede 12 saat ve özel boşluk karakteri); e-posta metni ve testi öngörülemez olur.

## Yetkilendirme ve kişisel veri

- Yeni uç yok; yetkilendirme yüzeyi değişmez.
- E-posta yalnızca siparişin alıcısına gider; içerikte adres, telefon ve takip numarası yoktur.
- Log satırı mevcut desende yalnızca sipariş numarasını yazar.

## Risks / Trade-offs

- **Gecikme:** teslim en çok bir sorgu aralığı (5 dakika) geç fark edilir; son an teslim kaydından hesaplandığı için kayma olmaz.
- **E-postası doğrulanmamış alıcı:** e-posta yine gider (L11'in borcu, ayrı change).
- **Pencere ayarı değişirse:** e-postadaki son an gönderildiği andaki ayarla hesaplanır. Sipariş detayı ise her istekte günceli hesaplar; ayar değiştirilirse ikisi ayrışabilir. Ayar değişikliği operasyonel bir karar olduğu için kabul edildi.

## Migration Plan

Şema ve sözleşme değişmez. Geri almak için `delivered` çağrısındaki bildirim kaldırılır.

## Open Questions

Yok.

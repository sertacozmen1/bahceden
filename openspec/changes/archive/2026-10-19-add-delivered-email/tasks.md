# Tasks: add-delivered-email

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7). Şema ve sözleşme değişmez; `web` etkilenmez.

## Faz 1 · `api`: teslim e-postası

- [x] 1.1 `common/time/DateTimeFormat.format(Instant, SupportedLocale)`: `Europe/Istanbul` (`BusinessDays.TURKEY`), sabit kalıp `d MMMM yyyy HH:mm`, ay adı hesabın dilinde (D2). Birim testi: iki dilde sabit bir an; UTC gece yarısına yakın an Türkiye gününe göre biçimlenir.
- [x] 1.2 `OrderNotifier.sellerOrderDelivered(sellerOrder, deliveredAt)`: alıcıya, hesabının dilinde; son an = `deliveredAt + bahceden.claims.window`; commit sonrası gönderim (D1).
- [x] 1.3 `ShipmentProgressService.delivered` geçiş uygulandığında bildirimi çağırır.
- [x] 1.4 `messages.properties` + `messages_en.properties`: `mail.sellerOrderDelivered.subject` / `.body` (sipariş numarası, üretici, son an "Türkiye saatiyle", süre saat olarak, bağlantı). `MessagesCatalogTest` yeşil.
- [x] 1.5 **Test (`critical`, `ShipmentPollingTest`):**
  - teslimde alıcıya tek e-posta, hesabın dilinde, son an ve sipariş bağlantısıyla;
  - e-postadaki son an, sipariş detayının `claimableUntil` değeriyle aynı anı gösterir;
  - `CREATED` → `DELIVERED` atlamasında önce "kargoya verildi", sonra "teslim edildi";
  - yinelenen teslim bildirimi ikinci e-posta üretmez;
  - satıcıya teslim e-postası gitmez.
- [x] 1.6 **Doğrulama:** `./mvnw -B verify` temiz (323 test, 0 hata; 2 yeni `DateTimeFormatTest`, 3 yeni ve 1 genişleyen `ShipmentPollingTest` testi; sözleşme değişmedi).

## Faz 2 · Uçtan uca doğrulama ve doküman

- [x] 2.1 `docker compose up -d --build`: sipariş → gönderi aç → sahte entegratör teslim eder → mailpit'te "teslim edildi" e-postası, son an sipariş detayındakiyle aynı. (Dev hesaplarıyla sipariş `BHC-6GZBBKGC98`: e-postalar sırasıyla alındı, yeni sipariş, kargoya verildi, teslim edildi; teslim 15:04 UTC, e-postada "Türkiye saatiyle 21 Ekim 2026 18:04", `claimableUntil` 2026-10-21T15:04Z. Ortamda Türkçe denendi; İngilizce gövde `ShipmentPollingTest`'te sabit.)
- [x] 2.2 ADR-091, ADR-092 + `docs/adr/index.md` + `docs/.tools/mkdocs.yml`; ADR-078'e genişletme notu.
- [x] 2.3 `docs/features/kargo-ve-teslimat.md` ve `ayipli-urun-ve-iade.md` (Tarihçe; bilinçli sınırlar), `docs/features/index.md`, `00-current-state.md` (L41 kalkar, test sayısı, sıradaki adım). `./docs/.tools/serve.sh --build` temiz. Ek: `kargo-ve-teslimat.md` bilinçli sınırlarındaki eskimiş "ayıplı ürün talebi yok" maddesi düzeltildi; `docs/how-to/yerel-gelistirme.md` kargo akışına teslim e-postası eklendi.
- [x] 2.4 Delta spec ana spec'e sync edilir.
- [x] 2.5 Archive kullanıcı onayıyla yapıldı (2026-10-19).

# change-cancel-rejection-syncs-shipment

**Durum:** Onaylandı (2026-10-26, proje sahibi), uygulandı ve kullanıcı onayıyla archive edildi (2026-10-27).

> Bu change [`00-current-state.md`](../../../../docs/reference/00-current-state.md) L36 sınırlamasını kapatır. [ADR-076](../../../../docs/adr/076-cancel-cancels-shipment-before-refund.md) "Olumsuz" bölümündeki maddeyi giderir; ADR-076'nın kararları (önce gönderi iptali, sonra iade; ya ikisi ya hiçbiri) aynen geçerlidir. `KALANLAR.md`'deki hiçbir maddeye bağlı değildir: entegratörün hangi firma olacağından bağımsız, port üzerinden çalışır.

## Why

Satıcı paketi şubeye verdiğinde sistem bunu bir sonraki durum sorgusunda öğrenir ([ADR-075](../../../../docs/adr/075-shipment-status-by-scheduled-polling.md)). Arada alıcı siparişi hâlâ `NEW` görür ve "İptal et" düğmesi açıktır. Düğmeye basınca entegratör iptali reddeder ve alıcı "kargo firmasına ulaşılamadı, biraz sonra tekrar deneyin" mesajını görür. Bu yanlış bir yönlendirmedir: tekrar denemek işe yaramaz, paket yoldadır. Alıcı ne olduğunu anlamaz ve destek ister.

## What Changes

- **`api`:**
  - İptal entegratörde reddedildiğinde, iptal transaction'ı geri alındıktan sonra o gönderinin durumu **hemen** entegratörden okunur. Durum sorgusuyla aynı kurallar uygulanır: gönderi ilerler, satıcı siparişi `SHIPPED` olur, alıcıya "kargoya verildi" e-postası gider.
  - Paket kargoya verilmiş çıkarsa yanıt `409 ORDER_ALREADY_SHIPPED`: "Paket kargo firmasına verildi; bu sipariş artık iptal edilemez. Teslim aldıktan sonra sorun bildirebilirsiniz."
  - Gönderi hâlâ şubeye verilmemiş görünüyorsa veya durum okunamıyorsa bugünkü yanıt aynen kalır: `502 SHIPPING_INTEGRATOR_ERROR`.
- **`web`:**
  - İptal penceresi `ORDER_ALREADY_SHIPPED` mesajını gösterir. Pencere kapanınca sayfa sunucudan yenilenir ve sipariş "Kargoda" durumunda, takip numarasıyla görünür.
- **Sözleşme:** yeni hata kodu `ORDER_ALREADY_SHIPPED`. Ekleyici; kırıcı değil.

## Capabilities

- **Değişen:** `orders` · "Kargoya verilmemiş satıcı siparişi iptal edilir ve tamamı iade edilir": entegratör iptali reddettiğinde gönderi durumunun hemen okunması ve `ORDER_ALREADY_SHIPPED` yanıtı.

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | `OrderCancellationService` (transaction sınırı, red sonrası durum okuma); `ShipmentStatusPoller.refreshActive(sellerOrderId)`; `ApiErrorCode.ORDER_ALREADY_SHIPPED`; `messages*.properties`; `OrderCancellationTest` (veya mevcut iptal testleri) genişler (`critical`) |
| `web/` | Evet | `CancelSellerOrder` (kapanışta yenileme); `ReasonDialog` isteğe bağlı kapanış geri çağrısı; testler. Yeni metin yok: mesaj `api`'den gelir |
| DB | Hayır | — |
| `shared/` | Evet | `openapi.yaml` yeniden üretilir (hata kodu enum'u) |
| `docs/` | Evet | ADR-105, ADR-106; ADR-076'ya not; `02-api-reference.md`; `kargo-ve-teslimat.md` ve `siparis-ve-odeme` feature Tarihçe; `00-current-state.md` (L36 kalkar) |

## Bağımlılık

Yeni paket yok (K7).

## Kişisel veri (K10)

Yeni kişisel veri yok. Log'a yalnızca satıcı siparişi ve gönderi kimlikleri yazılır; takip numarası yazılmaz.

## Non-goals

- Entegratör portunda "reddetti" ile "ulaşılamadı"yı ayırmak. Gerçek entegratörün hata biçimi A2 seçilmeden bilinmez; durumun okunması bu ayrımı gereksiz kılar.
- Durum sorgusunun aralığını kısaltmak. Pencereyi daraltır ama kapatmaz; yük artırır.
- Satıcının paketi şubeye verdiğini kendisinin bildirmesi (ADR-058: kargoya verme, entegratörden okunan olaydır).
- Kargoya verilmiş siparişte iptal talebi / iade akışı. Teslimden sonra ayıplı ürün talebi vardır (ADR-079).

## Riskler

- **İki dış çağrı:** red sonrası durum okuması iptal yanıtını bir entegratör çağrısı kadar uzatır. Yalnızca red durumunda olur.
- **E-posta yanıtla aynı anda:** alıcı iptal hatasını görürken "kargoya verildi" e-postası da gider. İkisi aynı gerçeği söyler; çelişmez.

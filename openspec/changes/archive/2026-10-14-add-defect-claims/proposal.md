# add-defect-claims

**Durum:** Onaylandı (2026-10-13, proje sahibi), uygulandı ve kullanıcı onayıyla archive edildi (2026-10-14); PR ile `main`'e gidiyor.

> Bu change şu kararlar üzerine kurulur:
> - [ADR-056](../../../../docs/adr/056-cancel-before-shipment-and-defect-refund.md) §3–6: teslimden sonra cayma yok; ayıplı veya hasarlı ürün için teslimden itibaren 48 saat içinde, en az bir fotoğrafla ve satır bazında talep; satıcı kabul eder, kısmi iade önerir ya da gerekçeyle reddeder; alıcı kabul etmezse veya satıcı süresinde yanıt vermezse admin karar verir; kısmi iadede komisyonun yüzde kısmı orantılı geri hesaplanır, sabit ücret ve kargo yalnızca tam iadede döner.
> - [ADR-055](../../../../docs/adr/055-commission-percent-plus-fixed-from-seller.md): komisyon %5 + 3 TL.
> - [ADR-072](../../../../docs/adr/072-shipments-table-and-seller-order-shipping-states.md): teslim zamanı gönderide (`shipments.delivered_at`).
> - [ADR-077](../../../../docs/adr/077-undelivered-order-admin-refund-or-close.md): admin karar yüzeyi ve "dönen taze ürün stoğa eklenmez" ilkesi.
>
> Ürün sahibinin 2026-10-13 tarihli kararları:
> - satıcının yanıt süresi **48 saat**; geçerse talep admin'e düşer;
> - kısmi iade **adet bazında**: iade = birim fiyat × adet;
> - talepte **1–3 fotoğraf**;
> - satıcı siparişi başına **tek talep**, birden çok satır;
> - satıcı kısmi iade önerir ya da reddederse alıcının **48 saati** var; yanıt vermezse teklif kabul edilmiş, ret kesinleşmiş sayılır.

## Why

Alıcı teslim edilen ezik, çürük veya eksik ürün için bugün sistemde hiçbir şey yapamıyor (`00-current-state.md` L1). Ayıplı mal hakkı yasal bir zorunluluktur (6502 sayılı Kanun) ve ADR-056 bu hakkın ürün içinde, izli ve fotoğraflı çözülmesini karara bağladı. Teslim zamanı `add-shipments` ile artık sistemde var; bu, akışın ön koşuluydu.

## What Changes

- **`api`, talep açma (alıcı):**
  - Alıcı, `DELIVERED` durumundaki satıcı siparişi için teslimden sonraki 48 saat içinde talep açar.
  - Talepte sorunlu satırlar ve her satır için adet (1…satın alınan adet), bir açıklama ve 1–3 fotoğraf bulunur.
  - Uç: `POST /api/v1/orders/{orderNumber}/seller-orders/{id}/claim` (multipart).
  - Fotoğraflar ilan fotoğrafı hattından geçer: döndürme, küçültme, konum bilgisinin atılması ([ADR-050](../../../../docs/adr/050-photo-processing-exif-orientation-two-sizes.md)).
  - Fotoğraflar yalnızca alıcıya, satıcıya ve admin'e korumalı olarak sunulur.
- **`api`, satıcı yanıtı:** satıcı 48 saat içinde üç yanıttan birini verir (`POST /api/v1/sellers/me/orders/{id}/claim/response`):
  - **kabul:** istenen adetlerin tamamı iade edilir;
  - **kısmi teklif:** daha az adet önerir;
  - **ret:** gerekçe zorunludur.
- **`api`, alıcının yanıtı:** satıcı kısmi teklif verdiyse ya da reddettiyse alıcı 48 saat içinde kabul eder (`…/claim/accept`) veya admin'e taşır (`…/claim/escalate`).
- **`api`, süreler:** Spring zamanlayıcısıyla çalışan tek bir iş süreleri işler:
  - satıcı yanıtlamadıysa talep admin'e düşer;
  - alıcı yanıtlamadıysa teklif kabul edilir (iade yapılır) ya da ret kesinleşir.
- **`api`, admin kararı:**
  - Uçlar: `GET /api/v1/admin/claims?status=`, `GET /api/v1/admin/claims/{id}`, `POST /api/v1/admin/claims/{id}/decision`.
  - Admin satır başına adetle iade eder ya da gerekçeyle reddeder.
- **`api`, para:**
  - İade tutarı = Σ birim fiyat × adet. Her satır ve adet tamamen iade edildiyse kargo ücreti de iade edilir.
  - Komisyon kalan ürün tutarı üzerinden yeniden hesaplanır; sabit ücret yalnızca tam iadede geri alınır. Yeni komisyon ve hakediş talebe yazılır.
  - İade satıcının alt üye payından, yeni `DEFECT` nedeniyle yapılır.
  - Stok geri eklenmez.
- **`api`, görüntüleme:**
  - Alıcının ve satıcının sipariş detayına talep bölümü eklenir: durum, satırlar, adetler, fotoğraflar, teklif, son tarih ve sonuç.
  - Satıcının hakedişi iadeden sonraki tutarı gösterir.
- **`api`, e-postalar:**
  - talep açıldı → satıcıya;
  - satıcı yanıtladı → alıcıya;
  - admin'e taşındı → iki tarafa;
  - sonuçlandı → iki tarafa.
  - E-postalar hesabın dilindedir.
- **`web`:**
  - Alıcı: sipariş detayında "Sorun bildir", talep formu (satır ve adet seçimi, açıklama, fotoğraf), talep kartı, teklifi kabul etme ve admin'e taşıma.
  - Satıcı: sipariş detayında talep kartı, kabul, adetli teklif ve gerekçeli ret.
  - Admin: `/admin/claims` liste ve detay; fotoğraflar, satır başına adetle iade ve gerekçeli ret.
- **Sözleşme:** yeni uçlar ve şemalar eklenir. `BuyerSellerOrderView` ve `SellerOrderDetail` şemalarına isteğe bağlı `claim` alanı, `SellerOrderDetail` şemasına iade sonrası hakediş eklenir. Kırıcı değişiklik yok.

## Capabilities

- **Yeni:** `defect-claims` (delta `specs/defect-claims/spec.md`): talep açma, satıcı yanıtı, alıcı yanıtı, süreler, admin kararı, para kuralı, fotoğrafların korumalı sunumu, e-postalar.
- **Değişen:** `orders` (MODIFIED "Satıcı kendi satıcı siparişlerini görür": iade sonrası hakediş ve talep bölümü; MODIFIED "Alıcı kendi siparişlerini görür": talep bölümü).

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | `order` paketinde `DefectClaim`, `DefectClaimLine`, `DefectClaimPhoto`, `DefectClaimChange`, `DefectClaimStatus`, `DefectClaimService` (açma, yanıt, karar), `DefectClaimSweep` (zamanlayıcı), controller'lar, DTO'lar, mapper. `OrderPricing`'e kısmi iade sonrası komisyon. `payment.RefundReason.DEFECT`. `catalog.PhotoProcessor` ve `common.storage.FileStorage` yeniden kullanılır. `BahcedenProperties.claims` (süreler, zamanlayıcı). `SecurityConfig`, `RateLimitPolicy` (talep açma), `ApiErrorCode`, mesajlar, e-postalar |
| `web/` | Evet | `features/claims` (api, bff, bileşenler); alıcı ve satıcı sipariş detayında talep kartı; `(account)/account/orders/[number]/claim` formu; `(admin)/admin/claims` liste ve detay; fotoğraf aktarma rotası; BFF uçları; mesajlar; testler |
| DB | Evet | `014-defect-claims.sql`: `defect_claims` (satıcı siparişi başına benzersiz), `defect_claim_lines`, `defect_claim_photos`, `defect_claim_changes` (yalnızca eklenir); `refunds_reason_check` kısıtına `DEFECT` eklenir. Ekleyici değişiklik |
| `shared/` | Evet (ekleyici) | Yeni uçlar ve şemalar; detay şemalarına isteğe bağlı alanlar |
| `docs/` | Evet | D1–D8 → ADR-079…086; `docs/product/ayipli-urun-iadesi.md`; yeni feature `docs/features/ayipli-urun-ve-iade.md` (D8); `kargo-ve-teslimat.md`, `sepet-ve-siparis.md` ve `odeme-ve-hakedis.md` Tarihçe; `01-domain-model.md`, `02-api-reference.md`; `00-current-state.md` (L1 daralır); `odeme-ve-hakedis.md` (hakediş düzeltmesi) |

## Bağımlılık

Yeni paket yok (K7). Zamanlayıcı Spring'in kendisidir; `add-shipments` ile zaten kurulu ([ADR-075](../../../../docs/adr/075-shipment-status-by-scheduled-polling.md)). Fotoğraf işleme yalnızca JDK ile yapılır ([ADR-050](../../../../docs/adr/050-photo-processing-exif-orientation-two-sizes.md)).

## Kişisel veri (K10)

- **Yeni kişisel veri:**
  - talep fotoğrafları: alıcının evinden çekilmiş görüntü olabilir; konum bilgisi atılır;
  - alıcının açıklaması;
  - satıcının gerekçesi.
- **Erişim:** fotoğraflar yalnızca o siparişin alıcısına, satıcısına ve admin'e sunulur. Herkese açık değildir, önbelleğe alınmaz, `nosniff` ile sunulur ([ADR-045](../../../../docs/adr/045-seller-documents-table-content-sniffing-protected-serving.md) deseni).
- **Log:** açıklama, gerekçe ve fotoğraf anahtarı log'a yazılmaz.
- **Saklama:** talep ve fotoğraflar silinmez (yasal kayıt). Saklama süresi KALANLAR A5'e bağlıdır; risk olarak notludur.

## Non-goals

- Ürünün satıcıya geri gönderilmesi (iade kargosu). Taze ürün geri dönmez.
- Kargo firmasına rücu (ADR-058 §6, sistem dışı).
- Talep sonrası ikinci talep ya da itiraz.
- Admin'e e-posta veya bildirim. Admin listeyi izler.
- Satıcı puanı veya ceza.
- Gerçek ödeme sağlayıcısında kısmi iadenin satıcı ve pazaryeri payına bölünmesi (KALANLAR A1; D4 açık soru).
- Kısmi iadeden sonra fatura veya e-arşiv düzeltmesi.

## Riskler

- **Kötüye kullanım:** alıcı her teslimde talep açabilir. Fotoğraf zorunluluğu, satıcı yanıtı ve admin hakemliği bunu sınırlar. Talep oranı ölçülür.
- **Zamanlayıcının parayı hareket ettirmesi:** alıcı yanıt vermezse iade, zamanlanmış iş tarafından yapılır.
  - Sağlayıcı hatasında talep değişmez ve bir sonraki çalışmada yeniden denenir. Satır kilidi çift iadeyi önler; bu davranış `critical` testle sabitlenir.
  - Zamanlayıcı her örnekte çalışır (L33 ile aynı sınır).
- **Sağlayıcı payı:** `PaymentProvider.refund` iadeyi satıcı payından düşer; komisyonun geri dönen kısmının pazaryeri payından karşılanması gerçek sağlayıcıyla netleşir (D4).
- **Hukuk:** 48 saat sınırı ve cayma istisnasının metni hukuk teyidi bekliyor (KALANLAR A5).

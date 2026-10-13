# Design: add-defect-claims

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altında bir ADR olur (`AGENTS.md` K1 / §5.2): D1 → ADR-079, D2 → ADR-080, D3 → ADR-081, D4 → ADR-082, D5 → ADR-083, D6 → ADR-084, D7 → ADR-085, D8 → ADR-086.
>
> Kabul edilmiş hiçbir ADR'ye aykırı karar yoktur. Kararlar şu çerçevenin uygulama ayrıntısıdır:
>
> | ADR | Çerçeve |
> |---|---|
> | [ADR-056](../../../../docs/adr/056-cancel-before-shipment-and-defect-refund.md) §3–6 | 48 saat, fotoğraf, satır bazlı talep, kısmi iade kuralı, admin hakemliği |
> | [ADR-055](../../../../docs/adr/055-commission-percent-plus-fixed-from-seller.md) | Komisyon |
> | [ADR-065](../../../../docs/adr/065-order-pricing-integer-minor-units-half-up.md) | Kuruş tamsayısı, `HALF_UP` |
> | [ADR-007](../../../../docs/adr/007-query-level-ownership-scoping.md), [ADR-044](../../../../docs/adr/044-seller-role-follows-status-with-query-gate.md) | Sorgu seviyesinde sahiplik, onaylı satıcı kapısı |
> | [ADR-050](../../../../docs/adr/050-photo-processing-exif-orientation-two-sizes.md), [ADR-045](../../../../docs/adr/045-seller-documents-table-content-sniffing-protected-serving.md) | Fotoğraf işleme, korumalı sunum |
> | [ADR-075](../../../../docs/adr/075-shipment-status-by-scheduled-polling.md) | Spring zamanlayıcısı |
> | [ADR-077](../../../../docs/adr/077-undelivered-order-admin-refund-or-close.md) | Admin karar yüzeyi; dönen taze ürün stoğa eklenmez |

## Kararlar

### D1 · Talep `order` paketinde; satıcı siparişi başına tek talep; satıcı siparişinin durumu değişmez, sonucu talep taşır

**Paket:** talep para ve sipariş kuralıdır. `order` paketinde `DefectClaim*` sınıfları olarak yaşar.
- Teslim zamanını `shipping`'den okur. Bu, mevcut `order` → `shipping` yönüyle aynıdır.
- İadeyi `payment` üzerinden yapar.
- Fotoğrafı `catalog.PhotoProcessor` ve `common.storage.FileStorage` ile işler (K9).

**Tek talep:** satıcı siparişi başına tek talep vardır (`defect_claims.seller_order_id` benzersiz) ve talep birden çok satır taşıyabilir. Bu, art arda gelen taleplerle iade toplamının satır tutarını aşmasını yapısal olarak önler.

**Satıcı siparişi durumu:** `DELIVERED` kalır. Talebin durumu ve sonucu (iade tutarı, yeni komisyon, yeni hakediş) talepte durur.
- Görünümler hakedişi talebin sonucundan okur.
- `REFUNDED` durumu teslim edilemeyen siparişin admin kararıdır ([ADR-077](../../../../docs/adr/077-undelivered-order-admin-refund-or-close.md)); ayıplı ürün iadesiyle karışmaz.

**Reddedilen alternatifler:**
- (a) Satıcı siparişine `CLAIMED` gibi yeni durumlar eklemek: kısmi iade sonrası sipariş "teslim edildi" olmaya devam eder; iki durum makinesi iç içe geçer.
- (b) Satır başına ayrı talep: her satıra ayrı karar ve ayrı iade gerekir; ürün sahibi tek talep seçti.
- (c) Ayrı bir `claim` paketi: talebin bütün kuralları sipariş tutarlarına dayanır; ayrı paket `order` iç tiplerini dışa açmayı gerektirir.

### D2 · Veri modeli ve talep durum makinesi

**Tablolar** (`014-defect-claims.sql`):

| Tablo | İçerik |
|---|---|
| `defect_claims` | `seller_order_id` (benzersiz), `status`, `description` (10–1000), `seller_reason`, `seller_deadline_at`, `buyer_deadline_at`, `refund_amount`, `shipping_refunded`, `commission_after`, `payout_after`, `resolved_by` (`SELLER` / `BUYER` / `ADMIN` / `DEADLINE`), `resolved_at`, zaman damgaları |
| `defect_claim_lines` | `claim_id`, `order_item_id` (talep içinde benzersiz), `claimed_quantity`, `offered_quantity`, `refunded_quantity` |
| `defect_claim_photos` | `claim_id`, `position` (0–2), `card_key`, `large_key`, boyutlar |
| `defect_claim_changes` | yalnızca eklenir: önceki ve yeni durum, eylemci hesap (boşsa süre işi), gerekçe, zaman |

**Durumlar** (`DefectClaimStatus`; geçişler `DefectClaimTransition`'da tek yerde, `SellerOrderTransition` deseni):

| Durum | Anlamı | Çıkışlar |
|---|---|---|
| `OPEN` | Satıcının yanıtı bekleniyor (48 saat) | satıcı kabul → `REFUNDED`; satıcı teklif → `OFFERED`; satıcı ret → `REJECTED`; süre → `ESCALATED` |
| `OFFERED` | Satıcı daha az adet önerdi; alıcının yanıtı bekleniyor (48 saat) | alıcı kabul veya süre → `REFUNDED`; alıcı taşır → `ESCALATED` |
| `REJECTED` | Satıcı reddetti; alıcının yanıtı bekleniyor (48 saat) | alıcı kabul veya süre → `CLOSED`; alıcı taşır → `ESCALATED` |
| `ESCALATED` | Admin kararı bekleniyor | admin iade → `REFUNDED`; admin ret → `CLOSED` |
| `REFUNDED` | İade yapıldı (tam veya kısmi); uç durum | — |
| `CLOSED` | İadesiz kapandı; uç durum | — |

**Kurallar:**
- Satıcının teklifi en az bir adet içerir ve her satırda alıcının istediği adeti aşmaz. Bütün satırlarda istenen adet önerilirse bu teklif değil kabuldür.
- Hiç adet önermemek retle aynıdır ve gerekçe ister.
- Admin her satırda **satın alınan** adete kadar iade edebilir, yani alıcının istediğinden fazlasına da karar verebilir. En az bir adet iade etmeyen admin kararı rettir ve gerekçe ister.

**Reddedilen alternatifler:**
- (a) Satıcının serbest tutar önermesi: ürün sahibi adet bazını seçti.
- (b) Durumu türetmek (saklamamak): süre işinin ve admin listesinin indeksli sorgusu durum kolonuna dayanır.

### D3 · Talep açma: 48 saatlik pencere, satır ve adet, açıklama, 1–3 fotoğraf; tek multipart istek

**Uç:** `POST /api/v1/orders/{orderNumber}/seller-orders/{id}/claim`, `multipart/form-data`.
- `claim` JSON parçası: `{lines: [{orderItemId, quantity}], description}`.
- `photos` parçası: 1–3 dosya.

**Koşullar:**
- **Sahiplik:** sipariş alıcının olmalıdır (`findByOrderNumberAndBuyerAccountId`); değilse 404.
- **Durum:** satıcı siparişi `DELIVERED` olmalıdır; değilse 409 `CLAIM_NOT_ALLOWED`.
- **Pencere:** gönderinin `delivered_at` anından itibaren `bahceden.claims.window` (`PT48H`) dolmamış olmalıdır; dolduysa 422 `CLAIM_WINDOW_CLOSED`.
- **Tek talep:** aynı satıcı siparişi için ikinci talep 409 `CLAIM_NOT_ALLOWED` alır; benzersiz kısıt eşzamanlı ikinci isteği de reddeder.
- **Satırlar:** her satır o satıcı siparişine ait olmalı; adet 1 ile satın alınan adet arasında olmalı; aynı satır iki kez gelemez. Bunlar alan hatası olarak döner.
- **Açıklama:** 10–1000 karakter.
- **Fotoğraflar:** 1–3 adet. Her biri ilan fotoğrafıyla aynı tür ve boyut sınırlarından ve `PhotoProcessor`'dan geçer; iki boyut üretilir, konum bilgisi atılır. Okunamayan fotoğraf alan hatasıdır.

**İşleme sırası:** talep tek transaction'dır. Satıcı siparişi kilitlenir (eşzamanlı ikinci talep sırayla işlenir), fotoğraflar işlenip yazılır ve transaction commit'lenmezse silinir (`FileStorage.writeWithinTransaction`; ilan fotoğrafının deseni ortak yardımcıya taşındı, K9).
*Uygulama notu (Faz 1):* ilk taslak fotoğrafları transaction dışında yazmayı öngörüyordu; mevcut ilan fotoğrafı deseni aynı garantiyi daha basit sağladığı için o kullanıldı.

**Satır kimliği:** alıcının satırı seçebilmesi için `OrderItemView` şemasına `id` alanı eklenir. Bu bir alan eklemesidir, kırıcı değildir.

**Hız sınırı:** talep açma `RateLimitPolicy`'ye eklenir: hesap başına saatte 10.

**Satıcının süresi:** `seller_deadline_at = şimdi + bahceden.claims.seller-response` (`PT48H`).

**Reddedilen alternatifler:**
- (a) Fotoğrafı ayrı uçla sonradan yüklemek: fotoğrafsız talep ara durumu doğar; ADR-056 en az bir fotoğrafı talebin parçası sayıyor.
- (b) Pencereyi satıcı siparişinin `updated_at` alanından hesaplamak: teslim anının tek kaynağı gönderidir (ADR-072).

### D4 · Para: iade = Σ birim fiyat × adet; tam iadede kargo ve bütün komisyon döner; kısmi iadede komisyon kalan tutar üzerinden yeniden hesaplanır; stok eklenmez

`OrderPricing`'e saf bir hesap eklenir: `afterRefund(itemsTotal, refunded, rateBp, fixed, full)`. Değerler satıcı siparişine sipariş anında yazılmış oran ve sabit ücrettir (ADR-055), bugünkü yapılandırma değildir. Bu hesap `critical` testle sabitlenir.

| Durum | İade tutarı | Komisyon | Hakediş |
|---|---|---|---|
| **Tam iade** (her satırın her adeti) | ürün tutarı + kargo ücreti | 0 | 0 |
| **Kısmi iade** | Σ birim fiyat × adet | `HALF_UP((itemsTotal − iade) × bp / 10000) + sabit` (kalan tutarı aşamaz) | `itemsTotal − iade − komisyon` |

**İadenin yapılması:**
- `PaymentService.refund(orderId, sellerOrderId, DEFECT, tutar, alt üye anahtarı)` çağrılır. `RefundReason.DEFECT` eklenir ve `refunds_reason_check` genişler.
- İade ve talebin `REFUNDED` olması aynı transaction'dadır. Sağlayıcı hatasında hiçbir şey değişmez: kullanıcı isteğinde 502 döner, süre işinde bir sonraki çalışmada yeniden denenir.
- Stok geri eklenmez: dönen taze ürün satılabilir sayılmaz ve ürün zaten geri gelmez ([ADR-077](../../../../docs/adr/077-undelivered-order-admin-refund-or-close.md) ile aynı ilke).

**Reddedilen alternatifler:**
- (a) Komisyonu iade tutarı oranında düşürmek (`komisyon × kalan / toplam`): sabit ücreti de orantılar; ADR-056 §5 sabit ücretin yalnızca tam iadede döneceğini söyler.
- (b) Kısmi iadede de kargo ücretini iade etmek: ADR-056 §5'e aykırı.

**Açık soru:** gerçek bölünmüş ödeme sağlayıcısında kısmi iadenin satıcı payı ile pazaryeri payı (geri dönen komisyon) arasındaki bölüşümü sağlayıcının API'sine bağlıdır. `PaymentProvider.refund` bugün yalnızca alt üye anahtarı ve tutar alır. Gerçek sağlayıcı seçilince (KALANLAR A1) port gerekirse genişletilir. Talep tablosundaki `commission_after` ve `payout_after` bu bölüşümün kaynağıdır.

### D5 · Süreler tek bir Spring zamanlama işinde; her talep kendi satır kilidiyle işlenir

`DefectClaimSweep`, `bahceden.claims.sweep-interval` (`PT5M`) aralıkla çalışır. `bahceden.claims.sweep-enabled` kapalıyken (testler) zamanlayıcı kurulmaz; testler işi sabit saatle doğrudan çağırır. Bu, [ADR-075](../../../../docs/adr/075-shipment-status-by-scheduled-polling.md)'teki gönderi sorgusunun desenidir.

Süresi geçmiş talepler indeksli sorguyla ve en çok `sweep-batch-size` (100) kadar okunur. Her talep ayrı bir transaction'da `SELECT … FOR UPDATE` ile kilitlenip yeniden denetlenir:

| Durum | Süre geçince |
|---|---|
| `OPEN` (satıcı süresi geçti) | `ESCALATED` |
| `OFFERED` (alıcı süresi geçti) | teklifteki adetlerle iade, `REFUNDED`; sağlayıcı hatasında talep `OFFERED` kalır ve `WARN` loglanır |
| `REJECTED` (alıcı süresi geçti) | `CLOSED` |

Eylemci boştur; geçmişe `DEADLINE` olarak yazılır. Kullanıcının eylemiyle süre işi aynı talebi aynı anda işlerse satır kilidi ikisini sıraya koyar ve ikincisi değişmiş durumu görüp çıkar. Bu davranış `critical` testle sabitlenir.

**Reddedilen alternatifler:**
- (a) Süreyi okuma anında türetmek: para hareketi okuma anında yapılamaz.
- (b) Her talep için ayrı zamanlanmış görev: bellekte durur, yeniden başlatmada kaybolur.
- (c) Kuyruk veya iş zamanlayıcı kütüphanesi: §3 ve K7.

### D6 · Yetki: alıcı, satıcı ve admin uçları ayrı; fotoğraflar üç tarafa korumalı sunulur

| Taraf | Uçlar | Denetim |
|---|---|---|
| Alıcı | `…/claim` (aç), `…/claim/accept`, `…/claim/escalate` | `orders/**` altında oturum ister; sipariş sorgusu alıcı kimliğiyle kurulur |
| Satıcı | `POST /api/v1/sellers/me/orders/{id}/claim/response` | önce onaylı satıcı kapısı, sonra `seller_id`'li kilitli sorgu |
| Admin | `/api/v1/admin/claims/**` | `ROLE_ADMIN` |
| Fotoğraf | `GET /api/v1/claims/{claimId}/photos/{position}?size=card\|large` | oturum ister; sorgu talebi alıcı, satıcı hesabı veya admin rolüyle bulur, aksi halde 404 |

Fotoğraf `ProtectedFileResponses` ile sunulur: `nosniff`, `Cache-Control: private, no-store`. `web` fotoğrafı kendi Route Handler'ıyla aktarır; satıcı belgesinin deseniyle aynıdır.

**Kurallar:**
- Başkasının talebi her uçta 404 döner.
- Durumu uymayan eylem 409 `CLAIM_INVALID_TRANSITION` alır.
- Satıcı yanıtının alanları (`decision`, `lines`, `reason`) alan hatası olarak döner.

**Reddedilen alternatif:** fotoğrafları ilan fotoğrafı gibi herkese açık sunmak. Alıcının evinden çekilmiş görüntüdür (K10).

### D7 · E-postalar: her durum değişikliğinde karşı tarafa, sonuçta iki tarafa

| Olay | Alıcıya | Satıcıya |
|---|---|---|
| Talep açıldı | — | e-posta: satırlar, adetler ve son tarih |
| Satıcı teklif verdi veya reddetti | e-posta: teklif ya da gerekçe ve alıcının son tarihi | — |
| Admin'e taşındı | e-posta | e-posta |
| Sonuçlandı (`REFUNDED` veya `CLOSED`) | e-posta: iade tutarı ya da gerekçe | e-posta: iade tutarı ve yeni hakediş ya da kapanış |

- E-postalar hesabın dilinde ve transaction sonrasında gider; `OrderNotifier` deseni kullanılır.
- Satıcı kabulü doğrudan sonuçtur; yalnızca "sonuçlandı" e-postası gider.
- Admin'e e-posta gitmez, admin listeyi izler (proposal non-goals).

### D8 · `web`: talep formu ayrı sayfada, talep kartı sipariş detaylarında, admin ekranı ayrı; feature kaydı "Ayıplı Ürün ve İade"

**Alıcı:**
- `/account/orders/[number]` sayfasında, pencere açık ve talep yokken satıcı siparişi kartında "Sorun bildir" bağlantısı görünür.
- Form `/account/orders/[number]/claim?sellerOrder={id}` sayfasındadır:
  - her satır için onay kutusu ve adet seçici (`QuantityStepper`, K9);
  - açıklama;
  - 1–3 fotoğraf seçimi; telefonda `accept="image/*"` ile kamera açılır.
- Talep kartı satıcı siparişi kartının içindedir: durum rozeti, satırlar (istenen, önerilen ve iade edilen adet), küçük fotoğraflar, son tarih ve sonuç. `OFFERED` ve `REJECTED` durumunda "Kabul et" (birincil) ve "Yönetime taşı" düğmeleri bulunur.

**Satıcı:**
- `/seller/orders/[id]` sayfasında talep kartı yer alır.
- `OPEN` durumunda üç eylem vardır:
  - "Tamamını kabul et" (birincil);
  - "Kısmi iade öner" (satır başına adet seçici);
  - "Reddet" (gerekçe penceresi, `ReasonDialog`, K9).
- Para etkisi her eylemde açıkça yazılır: "Alıcıya ₺X iade edilir, hakedişiniz ₺Y olur." Tutarı sunucu hesaplar; ekran gösterir.

**Admin:** `/admin/claims` sekmeli listedir (`ESCALATED` varsayılan; `OPEN`, `OFFERED`, `REJECTED`, `REFUNDED`, `CLOSED`). Detayda büyük fotoğraflar, iki tarafın beyanları, satır başına iade adedi ve gerekçeli ret bulunur.

**Feature kaydı:** "Ayıplı Ürün ve İade" yeni bir ürün seviyesi feature'dır (`docs/features/ayipli-urun-ve-iade.md`). Kargo ve sipariş dosyaları yalnızca Tarihçe satırı alır.

**Kısmi teklifte para etkisi:** ekran tutarı hesaplamaz (`AGENTS.md` §12.1). Satıcı kartının "kısmi iade öner" penceresi, sunucudaki bir önizleme ucunu çağırır (`POST /api/v1/sellers/me/orders/{id}/claim/preview`, satırlarla). Uç yan etkisizdir ve iade tutarı ile yeni hakedişi döner.

**Reddedilen alternatifler:**
- (a) Formu pencerede açmak: fotoğraf seçimi ve birden çok satır telefonda küçük pencerede zor.
- (b) Tutarı ekranda hesaplamak: §12.1.

## Risks / Trade-offs

- **Süre işi para hareket ettirir:** telafisi olmayan bir hata çift iade olurdu. Kilit, durum yeniden denetimi ve `critical` test bunu engeller.
- **Fotoğraf işleme istek içindedir:** üç fotoğraf ile ilan fotoğrafının üç katı süre gerekir (L26 ile aynı sınır).
- **Kötüye kullanım:** proposal Riskler.
- **İade tek sağlayıcı çağrısıdır:** ADR-067'deki olumsuz sonuç burada da geçerlidir. Commit öncesi iade yapılır, commit başarısız olursa iade kaydı oluşmaz; gerçek sağlayıcıda mutabakat gerekir.

## Migration Plan

1. **`014-defect-claims.sql`** (`--changeset bahceden:014-defect-claims`):
   - dört tablo, indeksler ve kısıtlar;
   - `refunds_reason_check` kısıtına `DEFECT` eklenir;
   - `--rollback`: tablolar düşürülür ve kısıt eski haline döner. `DEFECT` nedenli iade varsa kısıt geri kurulamaz; bu durum geri alma notunda yazılır.
2. Dev seed yazılmaz. Talep, geliştirmede akışla açılır; sahte entegratör gönderiyi zamanla "teslim edildi"ye ilerletir.

## Open Questions

- D4: gerçek sağlayıcıda kısmi iadenin pay bölüşümü (KALANLAR A1'e not).
- 48 saatlik pencere ve cayma istisnası metninin hukuk teyidi (KALANLAR A5).

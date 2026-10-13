# Tasks: add-defect-claims

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> **Yeni paket yok** (K7).
> **Şema değişikliği ekleyicidir:** dört yeni tablo ve genişleyen bir kısıt; veri kaybı yok.
> **Para etkisi:** her para kuralı ve geçiş `critical` testle sabitlenir.

## Faz 0 · Kararlar ve ürün belgesi (kod yok)

- [x] 0.1 D1–D8 → ADR-079…086 yazılır, `docs/adr/index.md` tablosuna ve `docs/.tools/mkdocs.yml` nav'ına eklenir.
- [x] 0.2 `docs/product/ayipli-urun-iadesi.md` şablondan yazılır ve `docs/product/index.md`'ye eklenir. İçerik:
  - kullanıcı problemi;
  - rakip analizi: büyük pazaryerlerinde "hasarlı ürün" talebi ve fotoğraf şartı;
  - kabul kriterleri (spec senaryolarıyla eşleşir);
  - başarı metrikleri: talep oranı, satıcı yanıt süresi, admin'e düşme oranı, kısmi ve tam iade oranı.
- [x] 0.3 `docs/features/ayipli-urun-ve-iade.md` açılır (Durum: `Geliştirmede`) ve `features/index.md` ile nav'a eklenir.
- [x] 0.4 **Doğrulama:** `./docs/.tools/serve.sh --build` temiz.

## Faz 1 · Talep açma ve görüntüleme: `api` → sözleşme → `web`

Bu faz bittiğinde alıcı talep açar; alıcı ve satıcı talebi görür. Henüz yanıt verilemez.

- [x] 1.1 `014-defect-claims.sql` (design Migration Plan 1) yazılır ve master changelog'a eklenir. Bütün şema bu tek changeset'tedir.
- [x] 1.2 Domain ve ayarlar:
  - entity'ler, `DefectClaimStatus`, `DefectClaimTransition`, repository'ler;
  - `BahcedenProperties.claims` (`window`, `seller-response`; açılışta doğrulanır). *Uygulama notu:* `buyer-response` ilk kullanıldığı Faz 2'ye kaldı (K6);
  - `RefundReason.DEFECT`.
- [x] 1.3 `DefectClaimService.open` (D3): sahiplik, durum, pencere, satırlar, fotoğraflar (`PhotoProcessor`), transaction dışında dosya yazma ve hatada silme, kayıt, geçmiş, satıcıya e-posta. Hız sınırı eklenir.
- [x] 1.4 Uçlar ve görünümler:
  - alıcı talep açma ucu (multipart);
  - korumalı fotoğraf ucu (D6);
  - `BuyerSellerOrderView` ve `SellerOrderDetail` şemalarına `claim` (ve alıcıya `claimableUntil`);
  - DTO'lar `@Schema` açıklaması ve örneğiyle;
  - `SecurityConfig`, `ApiErrorCode` (`CLAIM_NOT_ALLOWED`, `CLAIM_WINDOW_CLOSED`, `CLAIM_INVALID_TRANSITION`), mesajlar ve e-posta şablonları (`tr`, `en`).
- [x] 1.5 `openapi.yaml` yeniden üretilir.
- [x] 1.6 **Test (`critical`):**
  - mutlu yol;
  - 401 ve başkasının siparişi için 404;
  - teslim edilmemiş sipariş ve ikinci talep için 409;
  - pencere kapanmışsa 422;
  - satır ve adet hataları;
  - 0 ve 4 fotoğraf;
  - okunamayan fotoğraf;
  - fotoğraf erişimi: alıcı, satıcı ve admin görür, başkası 404, oturumsuz 401;
  - satıcıya e-posta;
  - detay görünümleri.
- [x] 1.7 **Doğrulama:** `./mvnw -B verify` temiz: 279 test, `BUILD SUCCESS` (2026-10-13).
  - *Uygulama notları:* multipart istek sınırı 3 fotoğraf için 31 MB'a çıktı; `PhotoProcessor` hata alanını parametre olarak alır; `OrderItemView`'a `id` eklendi (ekleme); `DefectClaimOpenTest` 8 test.
- [x] 1.8 `web`:
  - `npm run gen:api`;
  - `features/claims` (api, bff);
  - alıcı talep formu sayfası (satır ve adet, açıklama, fotoğraf);
  - "Sorun bildir" bağlantısı;
  - talep kartı (alıcı ve satıcı detayında);
  - fotoğraf aktarma Route Handler'ı;
  - metinler (`tr`, `en`).
- [x] 1.9 **Test (web):** form doğrulaması (satır seçilmedi, fotoğraf yok veya fazla, açıklama kısa), sunucu hatası eşlemesi, başarı yolu; talep kartı durumları; BFF köken reddi.
- [x] 1.10 **Doğrulama:** `lint`, `typecheck`, `test` (241 test, +15), `build` temiz (2026-10-13). `docker compose` üzerinde `014` uygulandı, servisler sağlıklı.
  - *Uygulama notları:* fotoğraf aktarma rotası `/media/claim-photos/[claimId]/[position]`; `cardSize` genişlik ve yükseklik taşıyan her tipi kabul eder (K9); talep formunun dönüş adresi oturum yoksa sipariş listesidir (typedRoutes).

## Faz 2 · Satıcı yanıtı, alıcı yanıtı ve para: `api` → `web`

- [x] 2.1 `OrderPricing.afterRefund` (D4) ve `critical` birim testleri: kısmi, tam, sınır (komisyon kalan tutarı aşamaz), yuvarlama.
- [x] 2.2 `DefectClaimService`'e eklenecekler:
  - satıcı yanıtı (kabul, teklif, ret) ve önizleme ucu;
  - alıcının kabulü ve yönetime taşıması;
  - iade (`PaymentService.refund`, `DEFECT`), talebe tutar, komisyon ve hakediş yazımı;
  - geçmiş ve e-postalar.
  Sağlayıcı hatasında 502 döner ve hiçbir şey değişmez.
- [x] 2.3 `SellerOrderDetail`'e iade sonrası komisyon ve hakediş; `openapi.yaml` yeniden üretilir.
- [x] 2.4 **Test (`critical`):**
  - her geçiş;
  - fazla teklif;
  - gerekçesiz ret;
  - yanlış durumda 409;
  - başka satıcı için 404;
  - iade tutarı ve sağlayıcıya giden pay;
  - stoğun değişmemesi;
  - sağlayıcı hatasında geri alma;
  - önizlemenin yan etkisiz olması;
  - e-postaların dili.
- [x] 2.5 **Doğrulama:** `./mvnw -B verify` temiz: 294 test, `BUILD SUCCESS` (2026-10-13).
  - *Uygulama notları:* `bahceden.claims.buyer-response` eklendi; satıcı yanıtı `SellerOrderDetail`, alıcı eylemleri `OrderDetail` döner; ortak test akışı `support/ClaimFlows`.
- [x] 2.6 `web`:
  - satıcı talep kartında üç eylem: kabul, adetli teklif (sunucu önizlemesiyle para etkisi), gerekçeli ret;
  - alıcı kartında kabul ve yönetime taşıma;
  - satıcı detayında iade sonrası hakediş;
  - testler.
- [x] 2.7 **Doğrulama:** `lint`, `typecheck`, `test` (254 test, +13), `build` temiz (2026-10-13).
  - *Uygulama notları:* `QuantityStepper`'a isteğe bağlı `min` eklendi (teklifte satır 0 olabilir, K9); kart eylemleri `ClaimCard`'a `actions` ile verilir; önizleme adet değişikliğinden 300 ms sonra istenir.

## Faz 3 · Süre işi: `api`

- [x] 3.1 `DefectClaimSweep` (D5): yapılandırma (`sweep-enabled`, `sweep-interval`, `sweep-batch-size`), testte kapalı. Süresi geçmiş taleplerin indeksli sorgusu, talep başına kilit ve yeniden denetim.
- [x] 3.2 **Test (`critical`):**
  - üç süre geçişi;
  - süre dolmadan dokunulmaması;
  - iade hatasında durumun değişmemesi ve sonraki çalışmada başarı;
  - kullanıcı eylemiyle eşzamanlılıkta tek iade.
- [x] 3.3 **Doğrulama:** `./mvnw -B verify` temiz: 302 test, `BUILD SUCCESS` (2026-10-13); `docker compose` üzerinde zamanlayıcı açık `api` sağlıklı açıldı.
  - *Uygulama notları:* süre işi talep başına `DefectClaimService.expire` çağırır (her talep kendi transaction'ında; iade hatası diğer talepleri durdurmaz); sıra en eski süreden; geçişler `DEADLINE_ESCALATE`, `DEADLINE_ACCEPT_OFFER`, `DEADLINE_CLOSE`; sözleşme değişmedi.

## Faz 4 · Admin kararı: `api` → `web`

- [x] 4.1 Admin uçları (liste, detay, karar) ve `openapi.yaml`.
  - *Uygulama notları:* karar öncesi para etkisi için yan etkisiz `POST /api/v1/admin/claims/{id}/preview` eklendi (satıcı önizlemesiyle aynı hesap); admin yalnızca talepteki satırları satın alınan adete kadar iade eder; kapanış e-postaları artık gerekçeyi (satıcının reddi ya da admin'in kararı) içerir.
- [x] 4.2 **Test (`critical`):** iade (satın alınana kadar adet) ve ret; gerekçesiz ret; yanlış durumda 409; 401 ve 403; süzgeç; sorgu sayısı.
- [x] 4.3 `web`: `(admin)/admin/claims` liste ve detay; admin gezinmesine bağlantı; testler.
- [x] 4.4 **Doğrulama:** `./mvnw -B verify`, `lint`, `typecheck`, `test`, `build` temiz.

## Faz 5 · Belgeler, uçtan uca doğrulama, sync

- [x] 5.1 `01-domain-model.md` (ER), `02-api-reference.md`, `00-current-state.md` (L1 daralır; yeni sınırlamalar), `docs/how-to/yerel-gelistirme.md` (talebi elle deneme).
- [x] 5.2 Feature Tarihçe satırları: `ayipli-urun-ve-iade.md`, `kargo-ve-teslimat.md`, `sepet-ve-siparis.md`, `odeme-ve-hakedis.md`; `features/index.md`.
- [x] 5.3 KALANLAR A1 notu: kısmi iadede pay bölüşümü.
- [x] 5.4 **Uçtan uca elle doğrulama** (`docker compose`):
  1. sipariş;
  2. gönderi;
  3. sahte entegratör teslim eder;
  4. alıcı talep açar;
  5. satıcı teklif verir;
  6. alıcı yönetime taşır;
  7. admin karar verir;
  8. mailpit e-postaları.
  - Ekranlar 375 ve 1440 px'de, iki temada görüntülenir.
- [x] 5.5 Delta spec'ler sync edilir; `orders` ve `shipments` Purpose satırları güncellenir.
- [x] 5.6 **Doğrulama:** §7 komutları ve docs strict derleme temiz.
- [x] 5.7 Archive **kullanıcı onayıyla** yapılır (§9).

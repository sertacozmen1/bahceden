# Tasks: change-cancel-rejection-syncs-shipment

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7). Şema değişmez.

## Faz 1 · `api`: red sonrası durum okuma ve 409 (D1)

- [x] 1.1 `ShipmentStatusPoller.refreshActive(sellerOrderId)`: etkin gönderiyi kendi transaction'ında `pollOne` ile günceller; kargoya verilmiş mi döner; okunamazsa `WARN` + `false`.
- [x] 1.2 `ApiErrorCode.ORDER_ALREADY_SHIPPED` (409); `messages.properties` + `messages_en.properties` `error.orderAlreadyShipped`.
- [x] 1.3 `OrderCancellationService`: iptal `TransactionTemplate` içinde; red sonrası `refreshActive`; 409 veya 502.
- [x] 1.4 `OpenApiContractTest -Dopenapi.write=true` ile `openapi.yaml` yeniden üretilir. (Hata kodları sözleşmede yanıt açıklamasında geçer; iki iptal ucunun 409 açıklaması güncellendi, 6 satır.)
- [x] 1.5 **Test (`critical`):** design.md "Test etkisi" listesi. (`ShipmentTest`e 3 test: alıcı iptali 409 + durum eşitleme + kargo e-postası, satıcı iptali 409, red + durum okunamaz 502. `ScriptedShippingIntegrator` gerçek entegratör gibi kargoya verilmiş gönderinin iptalini reddeder; mevcut red testi değişmeden yeşil.)
- [x] 1.6 **Doğrulama:** `./mvnw -B verify` temiz (359 test, 0 hata).

## Faz 2 · `web`: mesaj ve kapanışta yenileme (D2)

- [x] 2.1 `npm run gen:api`. (Tip değişikliği yok; hata kodları sözleşmede yalnızca açıklamada.)
- [x] 2.2 `ReasonDialog` isteğe bağlı `onClose`; `CancelSellerOrder` `ORDER_ALREADY_SHIPPED`'da kapanışta yeniler.
- [x] 2.3 Testler (`CancelSellerOrder`). (2 test: 409 mesajı pencerede, yenileme kapanışta; diğer hatada kapanışta yenileme yok.)
- [x] 2.4 **Doğrulama:** `npm run lint`, `typecheck`, `test`, `build` temiz (77 dosya, 317 test).

## Faz 3 · Uçtan uca doğrulama ve doküman

- [x] 3.1 `docker compose up -d --build`: gönderi açılmış siparişte, sahte entegratörün şubeye verme süresi geçtikten sonra ve durum sorgusundan önce alıcı iptal eder → 409 mesajı; pencere kapanınca "Kargoda"; mailpit'te "kargoya verildi" e-postası. (Pencere sabit kalsın diye durum sorgusu bu deneme için geçici bir compose override'ıyla kapatıldı, sonra normal ayarlarla yeniden başlatıldı. Sipariş BHC-FH5M426922: satıcı gönderi açtı, 2 dakika sonra alıcı iptal etti; pencerede mesaj, kapatınca "Kargoda" ve takip numarası; veritabanında satıcı siparişi SHIPPED, gönderi HANDED_OVER, iade yok; mailpit'te kargo e-postası.)
- [x] 3.2 ADR-105, ADR-106 + `docs/adr/index.md` + `docs/.tools/mkdocs.yml`; ADR-076'ya not.
- [x] 3.3 `docs/reference/02-api-reference.md`; `docs/features/` Tarihçe; `docs/features/index.md`; `00-current-state.md` (L36 kalkar, test sayıları, sıradaki adım). `./docs/.tools/serve.sh --build` temiz.

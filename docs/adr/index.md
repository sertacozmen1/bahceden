# Architecture Decision Records (ADR)

> **Biçim:** [Michael Nygard'ın ADR formatı](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)

## ADR nedir?

Architecture Decision Records, projedeki önemli mimari kararları ve bu kararların arkasındaki gerekçeleri belgeleyen kısa, yapılandırılmış dokümanlardır.

**Neden ADR tutuyoruz?**

- Yeni bir geliştirici "Neden GraphQL yerine OpenAPI kullanıyoruz?" diye sorduğunda cevap kodda değil ADR dosyasında bulunur.
- Kararın bağlamı (o andaki kısıtlar, değerlendirilen alternatifler) zamanla unutulur; ADR bunu önler.
- Kararı revize etmek gerektiğinde orijinal gerekçe ve kısıtlar bilinir: neyin değiştiğini görmek kolaylaşır.

**Bağlayıcılık:** Kabul edilmiş (`Accepted`) bir ADR, `AGENTS.md` dahil her şeyin üstündedir. Bir ADR'ye aykırı davranmak için önce o ADR'yi *superseded* eden yeni bir ADR yazılır ve kullanıcı onayı alınır (`AGENTS.md` §9).

---

## ADR listesi

| # | Başlık | Durum | Tarih |
|---|--------|-------|-------|
| 001 | [İki dağıtılabilir birim: Spring Boot REST API + Next.js web](001-spring-api-and-next-web.md) | Accepted | 2026-10-06 |
| 002 | [Bağımlılık bütçesi: Spring starters, Liquibase, Next.js çekirdeği](002-dependency-budget.md) | Accepted · `web/` bütçesi [027](027-shadcn-ui-design-system.md) ile değişti | 2026-10-06 |
| 003 | [`api/` ile `web/` arasındaki tek sözleşme OpenAPI dosyasıdır](003-openapi-as-single-contract.md) | Accepted | 2026-10-06 |
| 004 | [PostgreSQL ve şema değişikliği yalnızca Flyway ile](004-postgresql-with-flyway.md) | Superseded by [016](016-liquibase-for-schema-migrations.md) | 2026-10-06 |
| 005 | [Test stratejisi: Testcontainers, `critical` etiketi, sınırlı tarayıcı testi](005-test-strategy.md) | Accepted | 2026-10-06 |
| 006 | [Para minor unit tamsayı olarak taşınır; hesap sunucuda](006-money-as-minor-unit.md) | Accepted | 2026-10-06 |
| 007 | [Satıcı veri izolasyonu sorgu seviyesinde; varsayılan deny](007-query-level-ownership-scoping.md) | Accepted | 2026-10-06 |
| 008 | [Kimlik: JWT erişim + iptal edilebilir yenileme token'ı, tek hesap tablosu](008-jwt-auth-with-opaque-refresh.md) | Accepted · §4 [017](017-email-verification-non-blocking-and-2fa-switch.md) ile değişti | 2026-10-06 |
| 009 | [Sözleşme Spring kodundan üretilir; `openapi.yaml` commit'lenen çıktıdır](009-contract-generated-from-spring.md) | Accepted | 2026-10-06 |
| 010 | [Ürün yayın durumu enum'dur, boolean bayrak değil](010-product-status-as-enum.md) | Accepted | 2026-10-06 |
| 011 | [Liste sorguları fetch join; sorgu sayısı testle sabit](011-fetch-join-with-query-count-test.md) | Accepted | 2026-10-06 |
| 012 | [Sayfa boyutu üst sınırı sunucuda; aşan istek sınıra çekilir](012-page-size-capped-on-server.md) | Accepted | 2026-10-06 |
| 013 | [`sellers` tablosu onboarding kararından önce minimum doğar](013-minimal-seller-table-ahead-of-onboarding.md) | Accepted | 2026-10-06 |
| 014 | [Geliştirme verisi dev profilli tekrarlanabilir migration ile](014-dev-seed-via-profiled-repeatable-migration.md) | Accepted | 2026-10-06 |
| 015 | [Sözleşme tazeliği testle zorunlu; dosya testten üretilir](015-contract-freshness-enforced-by-test.md) | Accepted | 2026-10-06 |
| 016 | [Şema değişiklikleri Liquibase ile yönetilir](016-liquibase-for-schema-migrations.md) | Accepted | 2026-10-06 |
| 017 | [E-posta doğrulaması girişi engellemez; 2FA hesap anahtarıyla açılır](017-email-verification-non-blocking-and-2fa-switch.md) | Accepted · §2'nin "uç yok" cümlesi [103](103-user-toggles-two-factor-with-current-password.md) ile değişti | 2026-10-06 |
| 018 | [Yenileme token'ı: rotasyon ve tekrar kullanımda aile iptali](018-refresh-token-rotation-with-family-revocation.md) | Accepted · tolerans penceresi [069](069-refresh-token-reuse-grace-window.md) ile değişti | 2026-10-07 |
| 019 | [E-posta SMTP üzerinden, sağlayıcıdan bağımsız, akışı kesmeden](019-email-via-smtp-provider-agnostic.md) | Accepted | 2026-10-07 |
| 020 | [Tek kullanımlık token'lar: 256 bit, SHA-256 hash'li, süreli](020-verification-tokens-sha256-single-use.md) | Accepted | 2026-10-07 |
| 021 | [Kimlik yanıtları hesabın varlığını açığa çıkarmaz](021-identity-responses-do-not-reveal-accounts.md) | Accepted | 2026-10-07 |
| 022 | [Kimlik uçlarında bellek içi sabit pencere hız sınırı](022-in-memory-fixed-window-rate-limiting.md) | Accepted | 2026-10-07 |
| 023 | [`web` BFF: HttpOnly çerezler, başlık tabanlı CSRF, yönlendirmeyle yenileme](023-web-bff-cookies-and-header-csrf.md) | Accepted · yenileme tetikleyicisi [070](070-silent-session-refresh-on-any-page.md) ile genişledi | 2026-10-07 |
| 024 | [Yetkilendirme JWT iddialarıyla; `/accounts/me` taze okunur](024-roles-in-jwt-claims-fresh-me-endpoint.md) | Accepted | 2026-10-07 |
| 025 | [İkinci faktör kodu hesaba bağlı; ikinci adım e-posta + kod](025-second-factor-code-bound-to-account.md) | Accepted · son madde [103](103-user-toggles-two-factor-with-current-password.md) ile değişti | 2026-10-07 |
| 026 | [Doğrulama bağlantısını yeniden gönderme oturum ister](026-verification-link-resend-requires-session.md) | Accepted | 2026-10-07 |
| 027 | [Tasarım sistemi: shadcn/ui (Base UI) + Tailwind v4](027-shadcn-ui-design-system.md) | Accepted · tema maddesi [028](028-theme-choice-and-color-roles.md) ile değişti | 2026-10-07 |
| 028 | [Tema seçimi çerezle sunucuda; renkler iki katmanlı (merdiven + rol)](028-theme-choice-and-color-roles.md) | Accepted | 2026-10-07 |
| 029 | [`web` çevirisi: next-intl, yönlendirmesiz; metinler ICU biçiminde JSON'da](029-next-intl-without-routing.md) | Accepted | 2026-10-07 |
| 030 | [Dil seçimi: çerez → `Accept-Language` → Türkçe; aynı dil `api`'ye iletilir](030-locale-from-cookie-then-header.md) | Accepted | 2026-10-07 |
| 031 | [`api` metinleri `MessageSource` ile, `Accept-Language`'a göre; hata kodları sabit](031-api-messages-by-accept-language.md) | Accepted | 2026-10-07 |
| 032 | [Hesabın dili saklanır; kayıtta yazılır, girişte güncellenir; e-postalar o dilde](032-account-locale-stored-and-refreshed-on-login.md) | Accepted | 2026-10-07 |
| 033 | [Parola sıfırlama token'ı: ayrı tablo, 1 saat, yenisi eskileri kapatır](033-password-reset-token-separate-table-one-hour.md) | Accepted | 2026-10-07 |
| 034 | [Parola sıfırlama talebi hesabı açığa çıkarmaz; kayıtsız adrese e-posta gitmez](034-password-reset-request-reveals-nothing.md) | Accepted | 2026-10-07 |
| 035 | [Parola sıfırlama bütün oturumları kapatır; otomatik giriş yok](035-password-reset-signs-out-everywhere-no-auto-login.md) | Accepted | 2026-10-07 |
| 036 | [Sıfırlama bağlantısını kullanmak e-postayı doğrular](036-password-reset-marks-email-verified.md) | Accepted | 2026-10-07 |
| 037 | [Satıcı başvurusu: beyan + tek belge, manuel admin onayı](037-seller-application-with-document-and-manual-approval.md) | Accepted | 2026-10-08 |
| 038 | [Onaylı satıcının ilanı hemen yayına girer; admin gerekçeyle kaldırabilir](038-listings-publish-immediately-with-admin-takedown.md) | Accepted | 2026-10-08 |
| 039 | [Yüklenen dosyalar yerel diskte (Docker volume) durur, `api` sunar; boyutlandırma ImageIO ile](039-uploads-on-local-volume-served-by-api.md) | Accepted | 2026-10-08 |
| 040 | [Başvuru, satıcı kaydının kendisidir; durum geçmişi ayrı ve silinmeyen bir tabloda](040-seller-application-is-the-seller-record.md) | Accepted | 2026-10-08 |
| 041 | [Mevcut satıcılar onaylı sayılır; vitrin satıcı durumunu sorguda filtreler](041-storefront-filters-seller-status-in-query.md) | Accepted | 2026-10-08 |
| 042 | [Başvuru tek bir multipart istektir; yeniden gönderim de öyle](042-seller-application-single-multipart-request.md) | Accepted | 2026-10-08 |
| 043 | [Satıcı başvurusu için e-posta doğrulaması zorunludur](043-seller-application-requires-verified-email.md) | Accepted | 2026-10-08 |
| 044 | [`ROLE_SELLER` onayda eklenir, askıda kaldırılır; satıcıya özgü sorgular `APPROVED` durumunu da denetler](044-seller-role-follows-status-with-query-gate.md) | Accepted | 2026-10-08 |
| 045 | [Belge `seller_documents` tablosunda; tür içerikten tespit edilir; korumalı başlıklarla sunulur](045-seller-documents-table-content-sniffing-protected-serving.md) | Accepted | 2026-10-08 |
| 046 | [Admin yüzeyi `web` içinde `(admin)` rota grubunda; ilk admin veritabanından atanır](046-admin-surface-in-web-first-admin-by-sql.md) | Accepted | 2026-10-08 |
| 047 | [İlan durumlarına `REMOVED` eklenir; geçişler adlandırılmış ve tek yerde](047-listing-states-removed-and-named-transitions.md) | Accepted | 2026-10-08 |
| 048 | [Satıcı ilan uçları `/sellers/me/products` altında; önce onaylı satıcı kapısı, sonra sahiplik sorgusu](048-seller-listing-endpoints-gate-then-ownership.md) | Accepted | 2026-10-08 |
| 049 | [İlan fotoğrafları ayrı tabloda, tek tek yüklenir; sıra açık pozisyonla, kapak `position = 0`](049-listing-photos-table-one-by-one-position.md) | Accepted | 2026-10-08 |
| 050 | [Fotoğraf işleme: başlıktan piksel sınırı, EXIF yönü, iki boyut, JPEG yeniden kodlama; orijinal saklanmaz](050-photo-processing-exif-orientation-two-sizes.md) | Accepted | 2026-10-08 |
| 051 | [Fotoğraflar kayıtlı anahtarla herkese açık sunulur; `web` akış aktarır, `next/image` yeniden işlemez](051-photo-serving-public-key-registered-web-proxy.md) | Accepted | 2026-10-08 |
| 052 | [Vitrin kapağı ikinci sabit sorguyla gelir; ürün detayı `slug` ile, vitrinle aynı görünürlük koşuluyla](052-storefront-cover-second-query-detail-by-slug.md) | Accepted | 2026-10-08 |
| 053 | [Admin ilan denetimi: liste ve iki eylem; gerekçe diyaloğu ortak bileşen](053-admin-listing-moderation-shared-reason-dialog.md) | Accepted | 2026-10-08 |
| 054 | [Ödeme bölünmüş ödeme (alt üye işyeri) modeliyle alınır; sağlayıcı bir arayüzün arkasında, seçimi sonraya kalır](054-split-payment-via-provider-port-provider-pending.md) | Accepted | 2026-10-08 |
| 055 | [Komisyon satıcı payından: ürün tutarının %5'i + satıcı siparişi başına 3 TL; değerler siparişe yazılır](055-commission-percent-plus-fixed-from-seller.md) | Accepted | 2026-10-08 |
| 056 | [Kargodan önce iptal tam iadedir; teslimden sonra cayma yok, ayıplı ürün 48 saat içinde fotoğrafla bildirilir](056-cancel-before-shipment-and-defect-refund.md) | Accepted | 2026-10-08 |
| 057 | [Gönderi kargo entegratörü API'si ile oluşturulur; entegratör bir arayüzün arkasında, seçimi sonraya kalır](057-shipping-via-integrator-port-integrator-pending.md) | Accepted | 2026-10-08 |
| 058 | [Kargo ücreti satıcı başına sabit 99 TL, 1000 TL ve üzeri ücretsiz; maliyet pazaryerinde; kargoya verme süresi ilanda beyan edilir](058-flat-shipping-fee-per-seller-and-declared-handover.md) | Accepted | 2026-10-08 |
| 059 | [`payment` ve `order` paketleri; satın alınabilirlik tek koşul ve vitrine alan olarak çıkar](059-payment-and-order-packages-single-purchasability-rule.md) | Accepted | 2026-10-08 |
| 060 | [Satıcı ödeme hesabı: TCKN/VKN ve IBAN sunucuda doğrulanır, yanıtta maskelenir; alt üye kaydı eşzamanlı](060-seller-payout-account-validated-masked-sync-submerchant.md) | Accepted | 2026-10-08 |
| 061 | [İlanda kargoya verme süresi; son tarih iş günüyle, yalnızca hafta sonu dışlanır](061-listing-dispatch-days-business-days-weekends-only.md) | Accepted | 2026-10-08 |
| 062 | [Sepet sunucuda, hesaba bağlı; fiyat ve stok her okumada üründen](062-server-side-cart-prices-read-live.md) | Accepted | 2026-10-08 |
| 063 | [Teslimat illeri yapılandırmadan (İstanbul, İzmir, Ankara, Manisa); il kataloğu sabit; en çok 10 adres](063-delivery-provinces-config-address-book.md) | Accepted | 2026-10-08 |
| 064 | [Sipariş üç seviyede; tutarlar ve adres sipariş anında yazılır; `Idempotency-Key` zorunlu](064-three-level-order-snapshot-idempotency-key.md) | Accepted | 2026-10-08 |
| 065 | [Fiyatlama tek sınıfta: kuruş tamsayısı, `HALF_UP`; değerler yapılandırmada](065-order-pricing-integer-minor-units-half-up.md) | Accepted | 2026-10-08 |
| 066 | [Ödeme: başlat, yönlendir, tamamla; stok tamamlamada kilitle düşer, yetmezse tam iade](066-payment-redirect-verify-lock-stock-on-success.md) | Accepted | 2026-10-08 |
| 067 | [Kargodan önce iptal: alıcı her zaman, satıcı gerekçeyle; tam iade, stok geri eklenir](067-cancel-before-shipment-full-refund-restock.md) | Accepted · gönderisi açık siparişte [076](076-cancel-cancels-shipment-before-refund.md) ile genişledi | 2026-10-08 |
| 068 | [`web`: sepet ve ödeme vitrinde, siparişler hesapta, satıcı ekranları panelde; sepet sayısı sunucudan](068-checkout-web-routes-server-cart-count.md) | Accepted | 2026-10-08 |

| 069 | [Yenileme token'ı: 30 saniyelik tekrar kullanım toleransı](069-refresh-token-reuse-grace-window.md) | Accepted | 2026-10-09 |
| 070 | [Oturum her sayfada sessizce yenilenir; sekmeler Web Locks ile sıraya girer](070-silent-session-refresh-on-any-page.md) | Accepted | 2026-10-09 |
| 071 | [`shipping` paketi; entegratör dört metotlu bir arayüz, tek uygulama `fake`](071-shipping-package-integrator-port-fake-only.md) | Accepted | 2026-10-10 |
| 072 | [Gönderi ayrı tabloda; satıcı siparişi kargo durumlarıyla genişler](072-shipments-table-and-seller-order-shipping-states.md) | Accepted | 2026-10-10 |
| 073 | [Gönderi açma: koli boyutu, şubeye teslim, tekrarlanabilir istek](073-shipment-creation-package-size-branch-dropoff.md) | Accepted | 2026-10-10 |
| 074 | [Etiket korumalı sunulur; büyük puntolu gönderi kodu; sahte etiket fontsuz PDF](074-shipment-label-protected-serving-and-shipment-code.md) | Accepted | 2026-10-10 |
| 075 | [Gönderi durumu zamanlanmış sorguyla alınır](075-shipment-status-by-scheduled-polling.md) | Accepted | 2026-10-10 |
| 076 | [İptal önce gönderiyi iptal eder, sonra iade](076-cancel-cancels-shipment-before-refund.md) | Accepted | 2026-10-10 |
| 077 | [Teslim edilemeyen sipariş: admin iade eder veya gerekçeyle kapatır](077-undelivered-order-admin-refund-or-close.md) | Accepted | 2026-10-10 |
| 078 | [Kargo bildirimleri yalnızca e-posta, üç olay](078-shipping-notifications-email-only-three-events.md) | Accepted | 2026-10-10 |
| 079 | [Ayıplı ürün talebi `order` paketinde; satıcı siparişi başına tek talep; sonucu talep taşır](079-defect-claim-in-order-package-one-per-seller-order.md) | Accepted | 2026-10-13 |
| 080 | [Talep tabloları ve durum makinesi: açık, teklif, ret, yönetimde, iade edildi, kapandı](080-defect-claim-tables-and-state-machine.md) | Accepted | 2026-10-13 |
| 081 | [Talep açma: 48 saatlik pencere, satır ve adet, açıklama, 1–3 fotoğraf](081-defect-claim-opening-window-lines-photos.md) | Accepted | 2026-10-13 |
| 082 | [Ayıplı ürün iadesi: birim fiyat × adet; kısmi iadede komisyon kalan tutardan; stok eklenmez](082-defect-refund-amount-and-commission-recalculation.md) | Accepted | 2026-10-13 |
| 083 | [Talep süreleri zamanlanmış işte; alıcının sessizliği kabul sayılır](083-defect-claim-deadlines-by-scheduled-sweep.md) | Accepted | 2026-10-13 |
| 084 | [Talep uçlarında yetki; fotoğraflar yalnızca üç tarafa korumalı](084-defect-claim-authorization-and-protected-photos.md) | Accepted | 2026-10-13 |
| 085 | [Talep e-postaları: karşı tarafa, yönetime taşınınca ve sonuçta iki tarafa](085-defect-claim-emails.md) | Accepted · 3. madde [101](101-admin-email-on-claim-escalation.md) ile değişti | 2026-10-13 |
| 086 | [`web`: talep formu ayrı sayfada, kart sipariş detayında, admin ekranı; para etkisi sunucudan](086-defect-claim-web-surfaces.md) | Accepted | 2026-10-13 |
| 087 | [Oturum açıkken parola değiştirme: `POST /accounts/me/password-changes`, mevcut parola zorunlu](087-password-change-endpoint-requires-current-password.md) | Accepted | 2026-10-17 |
| 088 | [Yanlış mevcut parola alan hatasıdır; hesap başına hız sınırı](088-wrong-current-password-is-field-error-rate-limited-per-account.md) | Accepted | 2026-10-17 |
| 089 | [Parola değiştirme bütün oturumları kapatır (mevcut dahil); oturum açmaz; e-postayı doğrulamaz](089-password-change-signs-out-everywhere.md) | Accepted | 2026-10-17 |
| 090 | [`web`: parola değiştirme ayrı sayfada; BFF başarıda çerezleri siler](090-password-change-page-bff-clears-cookies.md) | Accepted | 2026-10-17 |
| 091 | ["Teslim edildi" e-postası: yalnızca alıcıya, sorun bildirimi süresiyle](091-delivered-email-to-buyer-with-claim-deadline.md) | Accepted | 2026-10-19 |
| 092 | [E-postalarda tarih-saat Türkiye saatiyle, sabit kalıpla, tek yardımcıda](092-email-datetime-turkey-time-fixed-pattern.md) | Accepted | 2026-10-19 |
| 093 | [Sipariş oluşturma için e-posta doğrulaması zorunlu: `422 ORDER_EMAIL_NOT_VERIFIED`](093-order-requires-verified-email.md) | Accepted | 2026-10-19 |
| 094 | [Ödeme sayfası doğrulanmamış alıcıya form yerine doğrulama paneli gösterir](094-checkout-page-shows-verification-panel.md) | Accepted | 2026-10-19 |
| 095 | [Geliştirme alıcısı yeni bir dev changeset'iyle doğrulanmış yapılır](095-dev-buyer-verified-by-new-seed.md) | Accepted | 2026-10-19 |
| 096 | [Ödeme dönüşü gelmeyen siparişler zamanlanmış işle, dönüşle aynı kuralla kapatılır](096-unpaid-orders-closed-by-scheduled-sweep.md) | Accepted | 2026-10-20 |
| 097 | [Süresi geçmiş kimlik kayıtları saklama payından sonra silinir; yenileme token'ları aileyle](097-expired-identity-records-deleted-refresh-by-family.md) | Accepted | 2026-10-20 |
| 098 | [Hesabın dili oturum sahibinin açık değerle çağırdığı bir uçla da değişir](098-account-locale-updated-by-explicit-endpoint.md) | Accepted | 2026-10-21 |
| 099 | [Dil menüsü önce çerezi yazar, hesabın dilini en iyi çabayla BFF üzerinden eşitler](099-locale-menu-syncs-account-best-effort.md) | Accepted | 2026-10-21 |
| 100 | [Süre işleri bir turda süresi dolmuş bütün kayıtları keyset sayfalamayla dolaşır; hata veren kayıt arkadakileri bekletmez](100-deadline-sweeps-page-past-failures.md) | Accepted | 2026-10-21 |
| 101 | [Talep yönetime düşünce her admin hesabına kendi dilinde e-posta gider](101-admin-email-on-claim-escalation.md) | Accepted | 2026-10-21 |
| 102 | [Oturum kapısının dönüş adresi sorguyu taşır; Next.js proxy isteğin tam adresini bir istek başlığına yazar](102-proxy-forwards-request-path-for-session-gate.md) | Accepted | 2026-10-21 |
| 103 | [Oturum sahibi iki adımlı girişi mevcut parolasıyla açar veya kapatır; açmak doğrulanmış e-posta ister](103-user-toggles-two-factor-with-current-password.md) | Accepted | 2026-10-26 |
| 104 | [İki adımlı giriş kendi sayfasında: durum, mevcut parola ve tek düğme; doğrulanmamış e-postada önce doğrulama](104-two-factor-page-current-password-single-button.md) | Accepted | 2026-10-26 |
| 105 | [Entegratör iptali reddedince gönderinin durumu hemen okunur; paket kargoya verilmişse 409 `ORDER_ALREADY_SHIPPED`](105-cancel-refusal-reads-shipment-status-409-when-shipped.md) | Accepted | 2026-10-26 |
| 106 | [İptal penceresi "kargoya verildi" mesajını gösterir; sayfa pencere kapanınca yenilenir](106-cancel-dialog-keeps-message-refreshes-on-close.md) | Accepted | 2026-10-26 |

**Sıradaki numara: 107.**

**Durum değerleri:** `Proposed` (öneri, henüz bağlayıcı değil) · `Accepted` (yürürlükte, bağlayıcı) · `Deprecated` (artık geçerli değil, yerine bir şey gelmedi) · `Superseded by [ADR-XXX]` (yerini başka bir karara bıraktı).

---

## Karar bekleyen konular

Aşağıdaki konular henüz karara bağlanmadı ve ilgili alanda kod yazılmasını **bloke eder**. Tam liste ve gerekçeler: `openspec/KALANLAR.md` ve `AGENTS.md` §15.

| Konu | Kaynak madde | Karara bağlanınca |
|---|---|---|
| Ödeme sağlayıcısı seçimi ve sözleşmesi (model, komisyon, iade karara bağlandı: ADR-054…056) | KALANLAR A1 | Gerçek sağlayıcı uygulaması için ADR |
| Kargo entegratörü seçimi ve sözleşmesi (entegrasyon biçimi ve ücret karara bağlandı: ADR-057, ADR-058) | KALANLAR A2 | Gerçek entegratör uygulaması için ADR |

---

## Yeni ADR nasıl yazılır?

1. [`template.md`](template.md) dosyasını kopyala: `NNN-kebab-case-baslik.md` (numara yukarıdaki "sıradaki numara"dan).
2. **Context**: kararın alındığı andaki durum, kısıtlar, tetikleyici. Gelecek zamanla değil, o anın gerçeğiyle yaz.
3. **Decision**: ne karar verildi; **değerlendirilen ve reddedilen alternatifler tablo halinde** ve her biri için ret gerekçesi. Alternatifsiz ADR eksik ADR'dir.
4. **Consequences**: olumlu, olumsuz ve nötr/trade-off sonuçlar. Olumsuz bölümü boş bırakılmaz; bedeli olmayan karar yoktur.
5. Bu dosyadaki tabloya satır ekle, "sıradaki numara"yı güncelle.
6. `docs/.tools/mkdocs.yml` nav bölümüne ekle.
7. İlgili `docs/features/` dosyasının *Tarihçe* bölümüne satır düş (`AGENTS.md` K1).

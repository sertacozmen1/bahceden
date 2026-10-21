# Güncel Durum ve Bilinen Sınırlamalar

**Son doğrulama:** 2026-10-21

> **Bu dosya projenin gerçeğidir.** Sunum, yol haritası, ürün belgesi veya herhangi bir planlama dokümanı "şu an ne var?" sorusuna cevap olarak kullanılamaz (`AGENTS.md` §5.4). Şüphede **kod** ve bu dosya esastır.
> Bir katmanın durumu, bilinen bir sınırlama veya sapma değiştiğinde bu dosya **aynı commit'te** güncellenir (`AGENTS.md` §6.4).

---

## 1. Tek cümlelik özet

**Yedi dikey dilim çalışıyor:** `docker compose up` ile vitrin, hesap, satıcı başvurusu, ürün ilanları, sepet-sipariş, kargo gönderisi ve ayıplı ürün iadesi uçtan uca ayakta (PostgreSQL + Spring Boot `api` + Next.js `web` + `mailpit`).

- **Hesap:** kullanıcı kayıt olur, e-postasını doğrular (doğrulama girişi engellemez), giriş yapar; oturumu arka planda yenilenir ve tarayıcı kapatılıp açılınca her sayfada sessizce geri gelir (ADR-069, ADR-070); çıkış yapar. İkinci faktörü veritabanından açılmış hesap e-postayla kod girer. Parolasını unutan kullanıcı e-postayla sıfırlar.
- **Satıcı başvurusu:** e-postası doğrulanmış kullanıcı belgesiyle satıcı olarak başvurur. Admin başvuruyu onaylar, reddeder, askıya alır veya askıdan çıkarır. Onaylı satıcı `ROLE_SELLER` alır ve vitrin yalnızca onaylı satıcıların ürünlerini gösterir.
- **Ürün ilanları:** onaylı satıcı ilan açar, 1–5 fotoğraf yükler (sunucuda döndürülür, küçültülür, konum bilgisi atılır), yayına alır; vitrin kartında kapak, `/products/[slug]` sayfasında galeri görünür. Admin uygunsuz ilanı gerekçeyle kaldırır ve yeniden açar.
- **Sepet ve sipariş:** onaylı satıcı ödeme bilgilerini girer; alıcı sepete ekler, teslimat adresini (İstanbul, İzmir, Ankara, Manisa) seçer ve sahte ödeme sağlayıcısıyla öder. Sipariş üreticilere bölünür; kargo ücreti üretici başına 99 TL (1000 TL ve üzeri ücretsiz), komisyon %5 + 3 TL. Stok ödeme anında düşer; kargodan önce alıcı veya satıcı iptal eder ve tam iade yapılır.
- **Kargo gönderisi:** satıcı koli boyutunu seçip sahte kargo entegratöründe gönderi açar, etiketi (PDF) yeni sekmede açar veya gönderi kodunu şubede söyler; paket şubeye teslim edilir. Gönderi durumu zamanlanmış sorguyla ilerler (kargoya verildi, yolda, teslim edildi, teslim edilemedi); alıcı firma ve takip numarasını görür ve "kargoya verildi" e-postası alır. Gönderisi açık sipariş iptal edilirse gönderi de iptal edilir. Teslim edilemeyen siparişte admin `/admin/orders`'tan tam iade eder veya gerekçeyle kapatır.
- **Ayıplı ürün iadesi:** alıcı teslimden sonraki 48 saat içinde sorunlu satırları, adetleri, açıklamayı ve 1–3 fotoğrafı bildirir. Üretici 48 saat içinde tamamını kabul eder, adetli kısmi iade önerir (para etkisi önizlenir) ya da gerekçeyle reddeder; yanıt vermezse talep Bahçeden ekibine düşer. Teklif veya retten sonra alıcı 48 saat içinde kabul eder ya da ekibe taşır; sessiz kalırsa teklif uygulanır, ret kesinleşir. Admin `/admin/claims`'ten satır başına adetle iade eder veya reddeder. İade üreticinin payından düşer, komisyon kalan tutar üzerinden yeniden hesaplanır, tam iadede kargo da döner; stok geri eklenmez. Her adımda e-posta gider.
- **Dil:** arayüz, sunucu mesajları ve e-postalar Türkçe ve İngilizce; oturum açıkken dil menüsünden yapılan seçim e-postaların dilini de değiştirir (ADR-098, ADR-099).

347 `api` testi ve 288 `web` testi yeşil. Gerçek ödeme sağlayıcısı ve gerçek kargo entegratörü **yok**; ikisi de sahte uygulamayla çalışır.

## 2. Katman durumu

| Katman | Durum | Kanıt |
|---|---|---|
| `AGENTS.md` (anayasa) | ✅ Yazıldı, yürürlükte | Depo kökü, 500 satır |
| `docs/adr/` | ✅ ADR-001…101 (004 superseded; 008 §4, 017 ile değişti; 002'nin `web/` bütçesi 027 ve 029 ile, 027'nin tema maddesi 028 ile, 085'in 3. maddesi 101 ile değişti) | [ADR index](../adr/index.md) |
| `docs/` (Diátaxis) | ✅ İskelet + şablonlar + beş how-to + sekiz feature kaydı + beş ürün belgesi + tasarım sistemi dökümü | `docs/` ağacı |
| `openspec/` | ✅ On altı change **archive edildi** | `changes/archive/2026-10-06-add-storefront-product-listing/`, `changes/archive/2026-10-07-add-account-and-session/`, `changes/archive/2026-10-07-add-i18n/`, `changes/archive/2026-10-07-add-password-reset/`, `changes/archive/2026-10-08-add-seller-onboarding/`, `changes/archive/2026-10-08-add-product-listings/`, `changes/archive/2026-10-08-add-cart-and-checkout/`, `changes/archive/2026-10-09-change-session-persistence/`, `changes/archive/2026-10-10-add-shipments/`, `changes/archive/2026-10-14-add-defect-claims/`, `changes/archive/2026-10-17-add-password-change/`, `changes/archive/2026-10-19-add-delivered-email/`, `changes/archive/2026-10-19-add-checkout-email-verification/`, `changes/archive/2026-10-20-add-stale-record-cleanup/`, `changes/archive/2026-10-21-add-account-locale-update/`, `changes/archive/2026-10-21-change-deadline-sweeps-and-admin-claim-email/`; açık change yok; `specs/storefront-catalog`, `specs/account-identity`, `specs/localization`, `specs/seller-onboarding`, `specs/product-listings`, `specs/seller-payout-details`, `specs/cart-and-checkout`, `specs/orders`, `specs/shipments` ve `specs/defect-claims` geçerli davranış sözleşmeleri |
| `.claude/` (opsx komutları + skill'ler) | ✅ Kurulu | `.claude/commands/opsx/`, `.claude/skills/` |
| `shared/contracts/openapi.yaml` | ✅ Üretildi ve commit'lendi | 73 uç, `bearerAuth` güvenlik şeması; liste şemaları öğe tipine göre ayrı (`PageResponse<Tip>`); `api` DTO'larından üretilir, tazeliği `OpenApiContractTest` ile zorunlu ([ADR-009](../adr/009-contract-generated-from-spring.md), [ADR-015](../adr/015-contract-freshness-enforced-by-test.md)) |
| `api/` (Spring Boot 3.5.16) | ✅ Çalışıyor | `catalog`, `account`, `seller`, `order`, `payment` ve `shipping` domainleri; ödeme sağlayıcısı arayüzü ve `fake` uygulaması ([ADR-054](../adr/054-split-payment-via-provider-port-provider-pending.md)); kargo entegratörü arayüzü, `fake` uygulaması ve zamanlanmış durum sorgusu ([ADR-071](../adr/071-shipping-package-integrator-port-fake-only.md), [ADR-075](../adr/075-shipment-status-by-scheduled-polling.md)); ayıplı ürün talebi ve süre işi `order/DefectClaim*` ([ADR-079](../adr/079-defect-claim-in-order-package-one-per-seller-order.md), [ADR-083](../adr/083-defect-claim-deadlines-by-scheduled-sweep.md)); dosya depolama `common/storage` (yerel volume, [ADR-039](../adr/039-uploads-on-local-volume-served-by-api.md)); fotoğraf işleme `catalog/PhotoProcessor` (yalnızca JDK, [ADR-050](../adr/050-photo-processing-exif-orientation-two-sizes.md)); JWT resource server, hız sınırlayıcı, SMTP; **Liquibase** changelog'ları `001`…`016` ([ADR-016](../adr/016-liquibase-for-schema-migrations.md)); kullanıcı metinleri `messages*.properties`, `Accept-Language` ile `tr` / `en` ([ADR-031](../adr/031-api-messages-by-accept-language.md)), hesabın dili `accounts.locale` ([ADR-032](../adr/032-account-locale-stored-and-refreshed-on-login.md)); 347 test yeşil (`./mvnw -B verify`) |
| `web/` (Next.js 16.3.8, React 19.3, TS 5.9.3) | ✅ Çalışıyor | Vitrin + hesap ekranları (`/register`, `/login`, `/verify-email`, `/account`, `/account/password`, `/forgot-password`, `/reset-password`) + satıcı başvurusu (`/seller/application`) + satıcı ilanları (`/seller/products/*`) + ürün detayı (`/products/[slug]`) + sepet ve ödeme (`/cart`, `/checkout`, `/checkout/return`) + siparişler, adresler ve sorun bildirimi (`/account/orders/*`, `/account/orders/[number]/claim`, `/account/addresses`) + satıcı siparişleri ve ödeme bilgileri (`/seller/orders/*`, `/seller/payout`) + admin (`/admin/sellers`, `/admin/sellers/[id]`, `/admin/products`, `/admin/orders`, `/admin/orders/[id]`, `/admin/claims`, `/admin/claims/[id]`) + fotoğraf aktarma (`/media/product-photos/[key]`, `/media/claim-photos/[claimId]/[position]`) + BFF Route Handler'ları (`/api/auth/*`, `/api/account/*`, `/api/seller/*`, `/api/admin/*`); tipler sözleşmeden üretilir; 288 Vitest testi; `lint`/`typecheck`/`build` temiz. Metinler next-intl ile `messages/tr.json` + `en.json`, dil çerez → tarayıcı → Türkçe ([ADR-029](../adr/029-next-intl-without-routing.md), [ADR-030](../adr/030-locale-from-cookie-then-header.md)). Tasarım sistemi Tailwind v4 + shadcn/ui `base-nova` (Base UI, Lucide, Geist); Açık / Koyu / Sistem tema menüsü, Türkçe / English dil menüsü ([ADR-027](../adr/027-shadcn-ui-design-system.md), [ADR-028](../adr/028-theme-choice-and-color-roles.md), [döküm](03-design-system.md)) |
| `docker-compose.yml` | ✅ Çalışıyor | `db` (postgres:17), `api` (8080), `web` (3000), `mailpit` (8025); api healthcheck'li; `uploads` volume'u `api`'de `/app/uploads` |
| `.githooks/pre-commit` | ✅ Devrede | `git config core.hooksPath .githooks` çalıştırıldı |
| `.github/workflows/ci.yml` | ✅ **4/4 yeşil** | Son koşu `37792192822` (PR #2 merge, ürün ilanları, 2026-10-08, 1m58s) |
| MkDocs portalı | ✅ Strict derleme temiz | `./docs/.tools/serve.sh --build`: ilk koşuda 18 uyarı (docs dışına link + git-revision eklentisi) düzeltildi |

## 3. Ortam (doğrulanmış)

Geliştirme makinesinde 2026-10-06'da doğrulanan sürümler:

| Araç | Sürüm | Not |
|---|---|---|
| JDK | Corretto **21.0.12.1** (LTS) | `~/.jdks/corretto-21.0.12.1`; `javac 21.0.12.1` çıktısıyla doğrulandı. Yanında Corretto 24.0.2 duruyor (proje sürümü değil). |
| Maven | **3.9.16** | IntelliJ IDEA bundle'ı; ayrı kurulum yapılmadı. JDK 21 ile çalıştığı doğrulandı. |
| Node / npm | **v24.0.0** / 11.7.0 | |
| Docker | Engine **29.6.2** (linux), Compose **v5.3.1** | |
| Git | 2.x | Depo: `sertacozmen1/bahceden` (private), branch `main`, tek commit |
| Liquibase | `liquibase-core` (Spring Boot BOM) | Changelog: `api/src/main/resources/db/changelog/`; bağlamlar: taban `default`, dev `default,dev` |
| `openspec` CLI | ❌ Kurulu değil | `.claude/skills/` ve `/opsx:*` komutları CLI'ı çağırır; CLI kurulana kadar bu komutlar çalışmaz |

## 4. Bilinen sınırlamalar

| # | Sınırlama | Etki |
|---|---|---|
| L1 | Gerçek ödeme ve gerçek kargo yok | Ödeme sahte sağlayıcıyla (her işlemi onaylar), gönderi sahte entegratörle (zamanla ilerler, "teslim edilemedi" üretmez) çalışır; canlıda ödeme alınamaz ve gönderi açılamaz. Ayıplı ürün iadesi sahte sağlayıcıda tek iade çağrısıyla yapılır; gerçek sağlayıcıda kısmi iadenin üretici ve pazaryeri payına bölünmesi sağlayıcı seçimine bağlı (KALANLAR A1) |
| L2 | `openspec` CLI kurulu değil | `/opsx:*` komutları çalışmaz; change artefaktları ve spec sync elle yapılıyor (yapı `AGENTS.md` §5.2'ye uygun) |
| L3 | Vitrin gözle doğrulanmadı | 375 px / 1440 px ve açık / koyu temada insan doğrulaması yapılmadı; CSS mobil önce yazıldı ve iki tema token'ı tanımlı |
| L4 | `pre-commit` hook'u kabuğun JAVA_HOME'unu kullanır | Hook `./mvnw` çağırırken oturumdaki JDK ile koşar; bu makinede Corretto 24 enjekte edildiği için testler yerelde 24, CI'da 21 üzerinde çalıştı. Sürüm farkı bugün soruna yol açmadı ama tek doğru sürüm 21'dir |
| L5 | `pre-commit` hook'u klon başına ayarlanır | `git config core.hooksPath .githooks` yeni klonda tekrar çalıştırılmalı. Bootstrap commit'leri `--no-verify` ile atıldı (dosyalar commit'lenmeden `git-revision` eklentisi strict derlemeyi kırıyor) |
| L8 | Ticari kararlar sonraki dilimleri blokluyor | Ödeme sağlayıcısı (A1) ve kargo entegratörü (A2) seçimi canlıda ödeme ve gönderiyi blokluyor; para akışı, komisyon, iade, kargo ücreti ve süre 2026-10-08'de karara bağlandı (ADR-054…058), sipariş dilimi `fake` sağlayıcılarla başlayabilir. MVP kapsamı (A4), bildirim kanalı (B4), barındırma (B5) ve satıcı verisinin saklama süresi / aydınlatma (A5) UNDECIDED. Satıcı onboarding (A3) ve dosya depolama (B3) 2026-10-08'de karara bağlandı (ADR-037…039) |
| L9 | Hız sınırı tek örnek içindir | Sayaçlar bellektedir: iki örnekte sınır ikiye katlanır, yeniden başlatma sayaçları sıfırlar ([ADR-022](../adr/022-in-memory-fixed-window-rate-limiting.md)) |
| L10 | İstemci adresi ağ düzenine güvenir | Next.js istemcinin gönderdiği `X-Forwarded-For` başlığını korur; `web` doğrudan internete açılırsa adres tabanlı hız sınırı sahte başlıkla atlatılabilir. Üretimde `web` önünde başlığı kendisi yazan bir reverse proxy gerekir (B5) |
| L13 | İkinci faktör ürün yüzeyinde kapalı | Anahtar yalnızca veritabanından veya dev seed'inden açılır; akış uygulanmış ve testli ama kullanıcı açamaz ([ADR-025](../adr/025-second-factor-code-bound-to-account.md)) |
| L14 | Hesap ekranları gözle doğrulanmadı | Akış `docker compose` üzerinde curl ile (tarayıcı başlıkları taklit edilerek) doğrulandı. Tasarım sistemi geçişinde (2026-10-07) vitrin, giriş, kayıt, geçersiz doğrulama bağlantısı ve bulunamadı ekranları başsız Chrome ekran görüntüsüyle 375 / 1440 px ve iki temada kontrol edildi; oturum açık başlık, "Hesabım" ve form hata durumları görüntülenmedi, gerçek cihazda insan doğrulaması hâlâ yapılmadı. 2026-10-08'de oturum açık başlık, "Hesabım" (satıcılık kartıyla), satıcı başvuru formu ve hata durumu, satıcı durum ekranı ve admin ekranları (liste, detay, gerekçe diyaloğu) başsız Chrome'da, oturum çereziyle 375 / 1440 px ve iki temada görüntülendi |
| L16 | `web` derlemesi ağ ister | `next/font` Geist'i derleme sırasında Google Fonts'tan indirir; ağsız ortamda `npm run build` ve Docker imajı derlemesi düşer ([ADR-027](../adr/027-shadcn-ui-design-system.md)) |
| L17 | `npm audit`: 5 yüksek önem bulgusu | Tamamı `eslint-config-next` → `@next/eslint-plugin-next` → `fast-glob` → `micromatch` → `braces` zincirinde; yalnızca geliştirme aracı (lint), çalışma zamanına girmez. Önerilen düzeltme `eslint-config-next@14`'e indirmek, yani kırıcı; Next yükseltmesiyle birlikte değerlendirilecek |
| L18 | İngilizce sayfalar ayrı indekslenmez | Dil URL'de görünmez ([ADR-030](../adr/030-locale-from-cookie-then-header.md)): arama motoru her adresi tek dilde (Türkçe) görür; İngilizce SEO yok |
| L20 | next-intl eklenti sarmalayıcısı kullanılmıyor | `createNextIntlPlugin` yerel ikili (`@swc/core`, `@parcel/watcher`) yüklediği ve bu makinede SWC önbellek izin denetimine takıldığı için takma ad `next.config.ts`'te elle verildi; next-intl yükseltmesinde sarmalayıcının yeni yapılandırması takip edilmeli ([ADR-029](../adr/029-next-intl-without-routing.md)) |
| L21 | Yüklenen belgeler antivirüsten geçmiyor | Tür içerikten tespit ediliyor, boyut sınırlı, belge yalnızca admin ve sahibine `nosniff` + `CSP: sandbox` ile sunuluyor ([ADR-045](../adr/045-seller-documents-table-content-sniffing-protected-serving.md)); tarama yok, kabul edilen risk |
| L22 | `uploads` volume'u yedeklenmiyor | Belgeler tek sunucunun diskinde; yedekleme veritabanıyla birlikte B5 kararına bağlı ([ADR-039](../adr/039-uploads-on-local-volume-served-by-api.md)) |
| L23 | Admin yetkisi yalnızca veritabanından | `ROLE_ADMIN` ürün yüzeyinden verilemez ([ADR-046](../adr/046-admin-surface-in-web-first-admin-by-sql.md), [rehber](../how-to/admin-yetkisi-verme.md)); rolü alınan admin'in token'ı en fazla 15 dakika daha geçerli |
| L24 | Satıcı belgeleri silinmiyor | Her gönderim yeni belge satırı; saklama süresi ve silme talebi hukuk kararı bekliyor (KALANLAR A5) |
| L25 | Seed ürünleri fotoğrafsız | `002` seed ürünleri ve hesapsız seed satıcıları vitrini besler; Liquibase volume'a dosya yazamadığı için fotoğrafları yoktur. Hesaplı onaylı dev satıcısı `010` seed'iyle geldi |
| L26 | Görüntü işleme istek içinde | Fotoğraf yükleme isteği ImageIO ile eşzamanlı işler (en çok 40 MP); hız sınırı saatte 60. Ayıplı ürün talebi üç fotoğrafı tek istekte işler (hız sınırı hesap başına saatte 10). Ölçüm sonrası gerekirse ayrı karar ([ADR-050](../adr/050-photo-processing-exif-orientation-two-sizes.md)) |
| L27 | Bazı JPEG'ler okunamaz | ImageIO CMYK gibi çeşitleri okuyamaz; kullanıcıya başka fotoğraf denemesi söylenir |
| L28 | Ödeme sahte sağlayıcıyla | Her ödemeyi onaylar, dışarı çıkmaz; canlıda ödeme alınamaz. Başarısız ödeme dev ortamında elle denenemez, testlerde sınanır (KALANLAR A1, [ADR-066](../adr/066-payment-redirect-verify-lock-stock-on-success.md)) |
| L29 | Aşırı satışta iade | Stok ödeme anında düşer; son ürünü iki alıcı öderse ikincisinin ödemesi tamamen iade edilir ([ADR-066](../adr/066-payment-redirect-verify-lock-stock-on-success.md)) |
| L30 | IBAN ve kimlik numarası düz metin | Yanıtta maskeli, log'da yok; veritabanında şifresiz. Disk ve yedek şifrelemesi KALANLAR B5 ([ADR-060](../adr/060-seller-payout-account-validated-masked-sync-submerchant.md)) |
| L31 | İş günü tatil bilmez | Kargoya verme son tarihi yalnızca hafta sonunu atlar; bayramlarda satıcı süre aşmış görünebilir (yalnızca bilgi) ([ADR-061](../adr/061-listing-dispatch-days-business-days-weekends-only.md)) |
| L33 | Gönderi durumu sorgusu tek örnek içindir | Zamanlayıcı her örnekte çalışır; iki örnekte iş tekrar eder (satır kilidi çift geçişi önler). Durum en çok bir sorgu aralığı kadar gecikir ([ADR-075](../adr/075-shipment-status-by-scheduled-polling.md)) |
| L34 | "Teslim edilemedi" dev'de denenemez | Sahte entegratör yalnızca teslim edildiye ilerler; teslim edilemeyen akışı ve admin kararı testlerde sınanır |
| L35 | Etiketler silinmiyor | Etiket alıcının adını ve adresini taşır; volume'da kalır, saklama süresi hukuk kararı bekliyor (KALANLAR A5) |
| L36 | Paket şubeye verildikten sonraki ilk sorguya kadar iptal açık görünür | Bu pencerede alıcının iptali entegratörde reddedilir ve "kargo firmasına ulaşılamadı" hatası görülür ([ADR-076](../adr/076-cancel-cancels-shipment-before-refund.md)) |
| L38 | Süre ve temizlik işleri tek örnek içindir | Talep süre işi, ödenmemiş sipariş işi ve kimlik kayıtları temizliği gönderi sorgusu gibi her örnekte çalışır ([ADR-096](../adr/096-unpaid-orders-closed-by-scheduled-sweep.md), [ADR-097](../adr/097-expired-identity-records-deleted-refresh-by-family.md)); satır kilidi ve durum yeniden denetimi çift iadeyi önler. Süre en çok bir tur aralığı (5 dakika) kadar gecikir ([ADR-083](../adr/083-defect-claim-deadlines-by-scheduled-sweep.md)) |
| L39 | Talep fotoğrafları ve açıklamaları silinmiyor | Alıcının evinden çekilmiş görüntü olabilir; yalnızca taraflara korumalı sunulur, konum bilgisi atılır. Saklama süresi hukuk kararı bekliyor (KALANLAR A5) |

## 5. Bilinen sapmalar (kod ↔ ADR/spec)

Bilinen sapma yok. Uygulama sırasında ortaya çıkan ve **karara bağlanan** farklar ADR'lere ve `openspec/changes/add-storefront-product-listing/tasks.md` içindeki S1–S11 tablosuna yazıldı; bu yüzden UNDECIDED bir sapma kalmadı.

Kural (`AGENTS.md` §5.4): kod ile ADR birbirine aykırıysa bu bir **sapma**dır; asistan hiçbirini sessizce değiştirmez, önce raporlar ve karar yoksa madde **UNDECIDED** olarak bu tabloya yazılır.

| # | Sapma | Durum |
|---|---|---|
| - | - | - |

## 6. Sıradaki adım

1. `fix-tabs-scroll-to-current` PR'ının incelenip `main`'e alınması.
2. Ticari: ödeme sağlayıcısı ve kargo entegratörü seçimi ve sözleşmeleri (KALANLAR A1, A2); kısmi iadenin pay bölüşümü A1'in parçası.
3. Hukuk: satıcı belgesinin yeterliliği, aydınlatma metni (IBAN, TCKN / VKN, sağlayıcıya ve entegratöre veri aktarımı), saklama süresi (belgeler, etiketler, talep fotoğrafları), mesafeli satış ve cayma metni, 48 saatlik ayıp bildirimi süresi (KALANLAR A5).

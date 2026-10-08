# Güncel Durum ve Bilinen Sınırlamalar

**Son doğrulama:** 2026-10-08

> **Bu dosya projenin gerçeğidir.** Sunum, yol haritası, ürün belgesi veya herhangi bir planlama dokümanı "şu an ne var?" sorusuna cevap olarak kullanılamaz (`AGENTS.md` §5.4). Şüphede **kod** ve bu dosya esastır.
> Bir katmanın durumu, bilinen bir sınırlama veya sapma değiştiğinde bu dosya **aynı commit'te** güncellenir (`AGENTS.md` §6.4).

---

## 1. Tek cümlelik özet

**Üç dikey dilim çalışıyor:** `docker compose up` ile vitrin, hesap ve satıcı başvurusu uçtan uca ayakta (PostgreSQL + Spring Boot `api` + Next.js `web` + `mailpit`).

- **Hesap:** kullanıcı kayıt olur, e-postasını doğrular (doğrulama girişi engellemez), giriş yapar; oturumu arka planda yenilenir; çıkış yapar. İkinci faktörü veritabanından açılmış hesap e-postayla kod girer. Parolasını unutan kullanıcı e-postayla sıfırlar.
- **Satıcı başvurusu:** e-postası doğrulanmış kullanıcı belgesiyle satıcı olarak başvurur. Admin başvuruyu onaylar, reddeder, askıya alır veya askıdan çıkarır. Onaylı satıcı `ROLE_SELLER` alır ve vitrin yalnızca onaylı satıcıların ürünlerini gösterir.
- **Dil:** arayüz, sunucu mesajları ve e-postalar Türkçe ve İngilizce.

133 `api` testi ve 93 `web` testi yeşil. Ürün ilanı ekleme, sepet, sipariş, ödeme ve kargo **yok**.

## 2. Katman durumu

| Katman | Durum | Kanıt |
|---|---|---|
| `AGENTS.md` (anayasa) | ✅ Yazıldı, yürürlükte | Depo kökü, 500 satır |
| `docs/adr/` | ✅ ADR-001…046 (004 superseded; 008 §4, 017 ile değişti; 002'nin `web/` bütçesi 027 ve 029 ile, 027'nin tema maddesi 028 ile değişti) | [ADR index](../adr/index.md) |
| `docs/` (Diátaxis) | ✅ İskelet + şablonlar + beş how-to + üç feature kaydı + bir ürün belgesi + tasarım sistemi dökümü | `docs/` ağacı |
| `openspec/` | ✅ Beş change **archive edildi** | `changes/archive/2026-10-06-add-storefront-product-listing/`, `changes/archive/2026-10-07-add-account-and-session/`, `changes/archive/2026-10-07-add-i18n/`, `changes/archive/2026-10-07-add-password-reset/`, `changes/archive/2026-10-08-add-seller-onboarding/`; açık change yok; `specs/storefront-catalog`, `specs/account-identity`, `specs/localization` ve `specs/seller-onboarding` geçerli davranış sözleşmeleri |
| `.claude/` (opsx komutları + skill'ler) | ✅ Kurulu | `.claude/commands/opsx/`, `.claude/skills/` |
| `shared/contracts/openapi.yaml` | ✅ Üretildi ve commit'lendi | 23 uç, `bearerAuth` güvenlik şeması; liste şemaları öğe tipine göre ayrı (`PageResponseProductSummary`, `PageResponseAdminSellerSummary`); `api` DTO'larından üretilir, tazeliği `OpenApiContractTest` ile zorunlu ([ADR-009](../adr/009-contract-generated-from-spring.md), [ADR-015](../adr/015-contract-freshness-enforced-by-test.md)) |
| `api/` (Spring Boot 3.5.16) | ✅ Çalışıyor | `catalog`, `account` ve `seller` domainleri; dosya depolama `common/storage` (yerel volume, [ADR-039](../adr/039-uploads-on-local-volume-served-by-api.md)); JWT resource server, hız sınırlayıcı, SMTP; **Liquibase** changelog'ları `001`…`008` ([ADR-016](../adr/016-liquibase-for-schema-migrations.md)); kullanıcı metinleri `messages*.properties`, `Accept-Language` ile `tr` / `en` ([ADR-031](../adr/031-api-messages-by-accept-language.md)), hesabın dili `accounts.locale` ([ADR-032](../adr/032-account-locale-stored-and-refreshed-on-login.md)); 133 test yeşil (`./mvnw -B verify`) |
| `web/` (Next.js 16.3.8, React 19.3, TS 5.9.3) | ✅ Çalışıyor | Vitrin + hesap ekranları (`/register`, `/login`, `/verify-email`, `/account`, `/forgot-password`, `/reset-password`) + satıcı başvurusu (`/seller/application`) + admin (`/admin/sellers`, `/admin/sellers/[id]`) + BFF Route Handler'ları (`/api/auth/*`, `/api/seller/*`, `/api/admin/*`); tipler sözleşmeden üretilir; 93 Vitest testi; `lint`/`typecheck`/`build` temiz. Metinler next-intl ile `messages/tr.json` + `en.json`, dil çerez → tarayıcı → Türkçe ([ADR-029](../adr/029-next-intl-without-routing.md), [ADR-030](../adr/030-locale-from-cookie-then-header.md)). Tasarım sistemi Tailwind v4 + shadcn/ui `base-nova` (Base UI, Lucide, Geist); Açık / Koyu / Sistem tema menüsü, Türkçe / English dil menüsü ([ADR-027](../adr/027-shadcn-ui-design-system.md), [ADR-028](../adr/028-theme-choice-and-color-roles.md), [döküm](03-design-system.md)) |
| `docker-compose.yml` | ✅ Çalışıyor | `db` (postgres:17), `api` (8080), `web` (3000), `mailpit` (8025); api healthcheck'li; `uploads` volume'u `api`'de `/app/uploads` |
| `.githooks/pre-commit` | ✅ Devrede | `git config core.hooksPath .githooks` çalıştırıldı |
| `.github/workflows/ci.yml` | ✅ **4/4 yeşil** | Son koşu `37760518873` (PR #1 merge, satıcı başvurusu, 2026-10-08, 1m50s) |
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
| L1 | Ticari akış yok | Vitrin ve hesap dışında kullanıcı akışı yok: ürün ekleme, sepet, sipariş, ödeme, kargo **yok** |
| L2 | `openspec` CLI kurulu değil | `/opsx:*` komutları çalışmaz; change artefaktları ve spec sync elle yapılıyor (yapı `AGENTS.md` §5.2'ye uygun) |
| L3 | Vitrin gözle doğrulanmadı | 375 px / 1440 px ve açık / koyu temada insan doğrulaması yapılmadı; CSS mobil önce yazıldı ve iki tema token'ı tanımlı |
| L4 | `pre-commit` hook'u kabuğun JAVA_HOME'unu kullanır | Hook `./mvnw` çağırırken oturumdaki JDK ile koşar; bu makinede Corretto 24 enjekte edildiği için testler yerelde 24, CI'da 21 üzerinde çalıştı. Sürüm farkı bugün soruna yol açmadı ama tek doğru sürüm 21'dir |
| L5 | `pre-commit` hook'u klon başına ayarlanır | `git config core.hooksPath .githooks` yeni klonda tekrar çalıştırılmalı. Bootstrap commit'leri `--no-verify` ile atıldı (dosyalar commit'lenmeden `git-revision` eklentisi strict derlemeyi kırıyor) |
| L8 | Ticari kararlar sonraki dilimleri blokluyor | Ödeme (A1), kargo (A2), MVP kapsamı (A4), bildirim kanalı (B4), barındırma (B5) ve satıcı verisinin saklama süresi / aydınlatma (A5) UNDECIDED. Satıcı onboarding (A3) ve dosya depolama (B3) 2026-10-08'de karara bağlandı (ADR-037…039) |
| L9 | Hız sınırı tek örnek içindir | Sayaçlar bellektedir: iki örnekte sınır ikiye katlanır, yeniden başlatma sayaçları sıfırlar ([ADR-022](../adr/022-in-memory-fixed-window-rate-limiting.md)) |
| L10 | İstemci adresi ağ düzenine güvenir | Next.js istemcinin gönderdiği `X-Forwarded-For` başlığını korur; `web` doğrudan internete açılırsa adres tabanlı hız sınırı sahte başlıkla atlatılabilir. Üretimde `web` önünde başlığı kendisi yazan bir reverse proxy gerekir (B5) |
| L11 | Doğrulanmamış e-postayla hesap açılabilir | [ADR-017](../adr/017-email-verification-non-blocking-and-2fa-switch.md)'nin kabul ettiği borç: sipariş dilimi `email_verified` kapısını değerlendirmek zorunda. Kapı ilk olarak satıcı başvurusuna kondu ([ADR-043](../adr/043-seller-application-requires-verified-email.md)) |
| L12 | Oturum açıkken parola değiştirilemez | Parola yalnızca e-postadaki sıfırlama bağlantısıyla değişir ([ADR-035](../adr/035-password-reset-signs-out-everywhere-no-auto-login.md)); "Hesabım"da parola değiştirme yok |
| L13 | İkinci faktör ürün yüzeyinde kapalı | Anahtar yalnızca veritabanından veya dev seed'inden açılır; akış uygulanmış ve testli ama kullanıcı açamaz ([ADR-025](../adr/025-second-factor-code-bound-to-account.md)) |
| L14 | Hesap ekranları gözle doğrulanmadı | Akış `docker compose` üzerinde curl ile (tarayıcı başlıkları taklit edilerek) doğrulandı. Tasarım sistemi geçişinde (2026-10-07) vitrin, giriş, kayıt, geçersiz doğrulama bağlantısı ve bulunamadı ekranları başsız Chrome ekran görüntüsüyle 375 / 1440 px ve iki temada kontrol edildi; oturum açık başlık, "Hesabım" ve form hata durumları görüntülenmedi, gerçek cihazda insan doğrulaması hâlâ yapılmadı. 2026-10-08'de oturum açık başlık, "Hesabım" (satıcılık kartıyla), satıcı başvuru formu ve hata durumu, satıcı durum ekranı ve admin ekranları (liste, detay, gerekçe diyaloğu) başsız Chrome'da, oturum çereziyle 375 / 1440 px ve iki temada görüntülendi |
| L15 | Süresi dolmuş token satırları birikir | `email_verification_tokens`, `password_reset_tokens`, `refresh_tokens`, `login_challenges` için temizleme işi yok; hacim sorun olursa ayrı karar |
| L16 | `web` derlemesi ağ ister | `next/font` Geist'i derleme sırasında Google Fonts'tan indirir; ağsız ortamda `npm run build` ve Docker imajı derlemesi düşer ([ADR-027](../adr/027-shadcn-ui-design-system.md)) |
| L17 | `npm audit`: 5 yüksek önem bulgusu | Tamamı `eslint-config-next` → `@next/eslint-plugin-next` → `fast-glob` → `micromatch` → `braces` zincirinde; yalnızca geliştirme aracı (lint), çalışma zamanına girmez. Önerilen düzeltme `eslint-config-next@14`'e indirmek, yani kırıcı; Next yükseltmesiyle birlikte değerlendirilecek |
| L18 | İngilizce sayfalar ayrı indekslenmez | Dil URL'de görünmez ([ADR-030](../adr/030-locale-from-cookie-then-header.md)): arama motoru her adresi tek dilde (Türkçe) görür; İngilizce SEO yok |
| L19 | Hesabın dili yalnızca girişte güncellenir | Oturum açıkken dili değiştiren kullanıcının sonraki e-postası (örn. yeni doğrulama bağlantısı) bir sonraki girişe kadar eski dilde gider ([ADR-032](../adr/032-account-locale-stored-and-refreshed-on-login.md)) |
| L20 | next-intl eklenti sarmalayıcısı kullanılmıyor | `createNextIntlPlugin` yerel ikili (`@swc/core`, `@parcel/watcher`) yüklediği ve bu makinede SWC önbellek izin denetimine takıldığı için takma ad `next.config.ts`'te elle verildi; next-intl yükseltmesinde sarmalayıcının yeni yapılandırması takip edilmeli ([ADR-029](../adr/029-next-intl-without-routing.md)) |
| L21 | Yüklenen belgeler antivirüsten geçmiyor | Tür içerikten tespit ediliyor, boyut sınırlı, belge yalnızca admin ve sahibine `nosniff` + `CSP: sandbox` ile sunuluyor ([ADR-045](../adr/045-seller-documents-table-content-sniffing-protected-serving.md)); tarama yok, kabul edilen risk |
| L22 | `uploads` volume'u yedeklenmiyor | Belgeler tek sunucunun diskinde; yedekleme veritabanıyla birlikte B5 kararına bağlı ([ADR-039](../adr/039-uploads-on-local-volume-served-by-api.md)) |
| L23 | Admin yetkisi yalnızca veritabanından | `ROLE_ADMIN` ürün yüzeyinden verilemez ([ADR-046](../adr/046-admin-surface-in-web-first-admin-by-sql.md), [rehber](../how-to/admin-yetkisi-verme.md)); rolü alınan admin'in token'ı en fazla 15 dakika daha geçerli |
| L24 | Satıcı belgeleri silinmiyor | Her gönderim yeni belge satırı; saklama süresi ve silme talebi hukuk kararı bekliyor (KALANLAR A5) |
| L25 | Seed satıcılarının hesabı yok | `ornek-uretici-*` satıcıları vitrini besler ama ürün yönetemez; ilan change'i seed'e hesaplı ve onaylı bir satıcı ekleyecek |

## 5. Bilinen sapmalar (kod ↔ ADR/spec)

Bilinen sapma yok. Uygulama sırasında ortaya çıkan ve **karara bağlanan** farklar ADR'lere ve `openspec/changes/add-storefront-product-listing/tasks.md` içindeki S1–S11 tablosuna yazıldı; bu yüzden UNDECIDED bir sapma kalmadı.

Kural (`AGENTS.md` §5.4): kod ile ADR birbirine aykırıysa bu bir **sapma**dır; asistan hiçbirini sessizce değiştirmez, önce raporlar ve karar yoksa madde **UNDECIDED** olarak bu tabloya yazılır.

| # | Sapma | Durum |
|---|---|---|
| - | - | - |

## 6. Sıradaki adım

1. **Ürün ilanı ekleme ve fotoğraf** ([ADR-038](../adr/038-listings-publish-immediately-with-admin-takedown.md), [ADR-039](../adr/039-uploads-on-local-volume-served-by-api.md)): change `add-product-listings` öneriliyor. Onaylı satıcı kapısı (`requireApprovedSeller`) ve `FileStorage` hazır.
2. Oturum açıkken parola değiştirme (L12). Karar alındı: bütün oturumlar kapanır.
3. Hukuk: satıcı belgesinin yeterliliği, aydınlatma metni, saklama süresi (KALANLAR A5).
4. Sipariş, ödeme ve kargo dilimleri KALANLAR A1 ve A2 kararlarını bekliyor.

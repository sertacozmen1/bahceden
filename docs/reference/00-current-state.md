# Güncel Durum ve Bilinen Sınırlamalar

**Son doğrulama:** 2026-10-07

> **Bu dosya projenin gerçeğidir.** Sunum, yol haritası, ürün belgesi veya herhangi bir planlama dokümanı "şu an ne var?" sorusuna cevap olarak kullanılamaz (`AGENTS.md` §5.4). Şüphede **kod** ve bu dosya esastır.
> Bir katmanın durumu, bilinen bir sınırlama veya sapma değiştiğinde bu dosya **aynı commit'te** güncellenir (`AGENTS.md` §6.4).

---

## 1. Tek cümlelik özet

**İki dikey dilim çalışıyor:** `docker compose up` ile vitrin ve hesap uçtan uca ayakta — PostgreSQL + Spring Boot `api` + Next.js `web` + `mailpit`. Kullanıcı kayıt olur, e-postasını doğrular (girişi engellemez), giriş yapar, oturumu arka planda yenilenir ve çıkış yapar; ikinci faktörü veritabanından açılmış hesap e-postayla kod girer. 79 `api` testi ve 30 `web` testi yeşil. Sepet, sipariş, ödeme ve kargo **yok**; bunlar `openspec/KALANLAR.md` maddelerine bağlı.

## 2. Katman durumu

| Katman | Durum | Kanıt |
|---|---|---|
| `AGENTS.md` (anayasa) | ✅ Yazıldı, yürürlükte | Depo kökü, 500 satır |
| `docs/adr/` | ✅ ADR-001…026 (004 superseded; 008 §4, 017 ile değişti) | [ADR index](../adr/index.md) |
| `docs/` (Diátaxis) | ✅ İskelet + şablonlar + üç how-to + iki feature kaydı | `docs/` ağacı |
| `openspec/` | ✅ İkinci change uygulandı, **archive onay bekliyor** | `changes/add-account-and-session/` (Faz 1–4 bitti, Faz 5 doküman); `specs/storefront-catalog` ve `specs/account-identity` geçerli davranış sözleşmeleri |
| `.claude/` (opsx komutları + skill'ler) | ✅ Kurulu | `.claude/commands/opsx/`, `.claude/skills/` |
| `shared/contracts/openapi.yaml` | ✅ Üretildi ve commit'lendi | 674 satır, 10 uç, `bearerAuth` güvenlik şeması; `api` DTO'larından üretilir, tazeliği `OpenApiContractTest` ile zorunlu ([ADR-009](../adr/009-contract-generated-from-spring.md), [ADR-015](../adr/015-contract-freshness-enforced-by-test.md)) |
| `api/` (Spring Boot 3.5.16) | ✅ Çalışıyor | `catalog` ve `account` domainleri; JWT resource server, hız sınırlayıcı, SMTP; **Liquibase** changelog'ları `001`…`004` ([ADR-016](../adr/016-liquibase-for-schema-migrations.md)); 79 test yeşil (`./mvnw -B verify`) |
| `web/` (Next.js 16.3.8, React 19.3, TS 5.9.3) | ✅ Çalışıyor | Vitrin + hesap ekranları (`/kayit`, `/giris`, `/dogrulama`, `/hesabim`) + BFF Route Handler'ları (`/api/auth/*`); tipler sözleşmeden üretilir; 30 Vitest testi; `lint`/`typecheck`/`build` temiz |
| `docker-compose.yml` | ✅ Çalışıyor | `db` (postgres:17), `api` (8080), `web` (3000), `mailpit` (8025); api healthcheck'li |
| `.githooks/pre-commit` | ✅ Devrede | `git config core.hooksPath .githooks` çalıştırıldı |
| `.github/workflows/ci.yml` | ✅ **4/4 yeşil** | Run `37589772992` (hesap dilimi): `api` 1m23s (79 test, Testcontainers), `web` 1m5s (30 test + build), `docs` 20s, kural denetimi 5s |
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
| L6 | `sellers` tablosu eksik | Hesap ilişkisi, vergi/belge ve onay alanları yok; satıcı onboarding kararıyla (A3) genişleyecek ([ADR-013](../adr/013-minimal-seller-table-ahead-of-onboarding.md)) |
| L7 | Üretici kaydı yalnızca seed ile | Üreticiler dev bağlamındaki changeset ile oluşuyor; başvuru/onay akışı yok |
| L8 | Ticari kararlar sonraki dilimleri blokluyor | Ödeme (A1), kargo (A2), satıcı onboarding (A3), MVP kapsamı (A4), fotoğraf depolama (B3), bildirim kanalı (B4), barındırma (B5) UNDECIDED |
| L9 | Hız sınırı tek örnek içindir | Sayaçlar bellektedir: iki örnekte sınır ikiye katlanır, yeniden başlatma sayaçları sıfırlar ([ADR-022](../adr/022-in-memory-fixed-window-rate-limiting.md)) |
| L10 | İstemci adresi ağ düzenine güvenir | Next.js istemcinin gönderdiği `X-Forwarded-For` başlığını korur; `web` doğrudan internete açılırsa adres tabanlı hız sınırı sahte başlıkla atlatılabilir. Üretimde `web` önünde başlığı kendisi yazan bir reverse proxy gerekir (B5) |
| L11 | Doğrulanmamış e-postayla hesap açılabilir | [ADR-017](../adr/017-email-verification-non-blocking-and-2fa-switch.md)'nin kabul ettiği borç: sipariş dilimi `email_verified` kapısını değerlendirmek zorunda |
| L12 | Parola sıfırlama yok | Parolasını unutan kullanıcı hesabına giremez ve yeni doğrulama bağlantısı da isteyemez (oturum ister, [ADR-026](../adr/026-verification-link-resend-requires-session.md)); sıradaki en küçük dilim |
| L13 | İkinci faktör ürün yüzeyinde kapalı | Anahtar yalnızca veritabanından veya dev seed'inden açılır; akış uygulanmış ve testli ama kullanıcı açamaz ([ADR-025](../adr/025-second-factor-code-bound-to-account.md)) |
| L14 | Hesap ekranları gözle doğrulanmadı | Akış `docker compose` üzerinde curl ile (tarayıcı başlıkları taklit edilerek) doğrulandı; gerçek tarayıcıda 375 / 1440 px ve iki temada insan doğrulaması yapılmadı |
| L15 | Süresi dolmuş token satırları birikir | `email_verification_tokens`, `refresh_tokens`, `login_challenges` için temizleme işi yok; hacim sorun olursa ayrı karar |

## 5. Bilinen sapmalar (kod ↔ ADR/spec)

Bilinen sapma yok. Uygulama sırasında ortaya çıkan ve **karara bağlanan** farklar ADR'lere ve `openspec/changes/add-storefront-product-listing/tasks.md` içindeki S1–S11 tablosuna yazıldı; bu yüzden UNDECIDED bir sapma kalmadı.

Kural (`AGENTS.md` §5.4): kod ile ADR birbirine aykırıysa bu bir **sapma**dır; asistan hiçbirini sessizce değiştirmez, önce raporlar ve karar yoksa madde **UNDECIDED** olarak bu tabloya yazılır.

| # | Sapma | Durum |
|---|---|---|
| - | - | - |

## 6. Sıradaki adım

1. Vitrin ve hesap ekranlarının iki ekran genişliği ve iki temada gözle doğrulanması (L3, L14; insan işi).
2. `add-account-and-session` change'inin archive'ı (kullanıcı onayı; `AGENTS.md` §9).
3. Sonraki dilim adayı: **parola sıfırlama** (L12): doğrulama token altyapısının üzerine oturur, en küçük adım.

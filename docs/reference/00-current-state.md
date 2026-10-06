# Güncel Durum ve Bilinen Sınırlamalar

**Son doğrulama:** 2026-10-06

> **Bu dosya projenin gerçeğidir.** Sunum, yol haritası, ürün belgesi veya herhangi bir planlama dokümanı "şu an ne var?" sorusuna cevap olarak kullanılamaz (`AGENTS.md` §5.4). Şüphede **kod** ve bu dosya esastır.
> Bir katmanın durumu, bilinen bir sınırlama veya sapma değiştiğinde bu dosya **aynı commit'te** güncellenir (`AGENTS.md` §6.4).

---

## 1. Tek cümlelik özet

**İlk dikey dilim çalışıyor:** `docker compose up` ile vitrin uçtan uca ayakta — PostgreSQL + Spring Boot `api` + Next.js `web`. Vitrin yayınlanmış ürünleri üreticisiyle listeliyor; 20 `api` testi ve 6 `web` testi yeşil. Kimlik doğrulama, sepet, sipariş, ödeme ve kargo **yok**; bunlar `openspec/KALANLAR.md` maddelerine bağlı.

## 2. Katman durumu

| Katman | Durum | Kanıt |
|---|---|---|
| `AGENTS.md` (anayasa) | ✅ Yazıldı, yürürlükte | Depo kökü, 460 satır |
| `docs/adr/` | ✅ ADR-001…016 (004 superseded) | [ADR index](../adr/index.md) |
| `docs/` (Diátaxis) | ✅ İskelet + şablonlar + bir how-to | `docs/` ağacı |
| `openspec/` | ✅ İlk change **archive edildi** | `changes/archive/2026-10-06-add-storefront-product-listing/`; `specs/storefront-catalog/spec.md` geçerli davranış sözleşmesi; açık change yok |
| `.claude/` (opsx komutları + skill'ler) | ✅ Kurulu | `.claude/commands/opsx/`, `.claude/skills/` |
| `shared/contracts/openapi.yaml` | ✅ Üretildi ve commit'lendi | 159 satır; `api` DTO'larından üretilir, tazeliği `OpenApiContractTest` ile zorunlu ([ADR-009](../adr/009-contract-generated-from-spring.md), [ADR-015](../adr/015-contract-freshness-enforced-by-test.md)) |
| `api/` (Spring Boot 3.5.16) | ✅ Çalışıyor | `catalog` domaini, `GET /api/v1/products`, `GET /api/v1/health`; **Liquibase** changelog'ları ([ADR-016](../adr/016-liquibase-for-schema-migrations.md)); 20 test yeşil (`./mvnw -B verify`) |
| `web/` (Next.js 16.3.8, React 19.3, TS 5.9.3) | ✅ Çalışıyor | Vitrin listesi + dört durum + `AppShell`; tipler sözleşmeden üretilir; 6 Vitest testi; `lint`/`typecheck`/`build` temiz |
| `docker-compose.yml` | ✅ Çalışıyor | `db` (postgres:17), `api` (8080), `web` (3000), `mailpit` (8025); api healthcheck'li |
| `.githooks/pre-commit` | ✅ Devrede | `git config core.hooksPath .githooks` çalıştırıldı |
| `.github/workflows/ci.yml` | ✅ **4/4 yeşil** | Run `37520245480`: `api` 1m9s (Maven + Testcontainers **gerçekten koştu**), `web` 46s, `docs` 20s, kural denetimi 4s |
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
| L1 | Yalnızca okuma akışı var | Vitrin listesi dışında hiçbir kullanıcı akışı yok: kayıt, giriş, ürün ekleme, sepet, sipariş, ödeme, kargo **yok** |
| L2 | `openspec` CLI kurulu değil | `/opsx:*` komutları çalışmaz; change artefaktları ve spec sync elle yapılıyor (yapı `AGENTS.md` §5.2'ye uygun) |
| L3 | Vitrin gözle doğrulanmadı | 375 px / 1440 px ve açık / koyu temada insan doğrulaması yapılmadı; CSS mobil önce yazıldı ve iki tema token'ı tanımlı |
| L4 | `pre-commit` hook'u kabuğun JAVA_HOME'unu kullanır | Hook `./mvnw` çağırırken oturumdaki JDK ile koşar; bu makinede Corretto 24 enjekte edildiği için testler yerelde 24, CI'da 21 üzerinde çalıştı. Sürüm farkı bugün soruna yol açmadı ama tek doğru sürüm 21'dir |
| L5 | `pre-commit` hook'u klon başına ayarlanır | `git config core.hooksPath .githooks` yeni klonda tekrar çalıştırılmalı. Bootstrap commit'leri `--no-verify` ile atıldı (dosyalar commit'lenmeden `git-revision` eklentisi strict derlemeyi kırıyor) |
| L6 | `sellers` tablosu eksik | Hesap ilişkisi, vergi/belge ve onay alanları yok; satıcı onboarding kararıyla (A3) genişleyecek ([ADR-013](../adr/013-minimal-seller-table-ahead-of-onboarding.md)) |
| L7 | Üretici kaydı yalnızca seed ile | Üreticiler dev bağlamındaki changeset ile oluşuyor; başvuru/onay akışı yok |
| L8 | Ticari kararlar sonraki dilimleri blokluyor | Ödeme (A1), kargo (A2), satıcı onboarding (A3), MVP kapsamı (A4), fotoğraf depolama (B3), bildirim kanalı (B4), barındırma (B5) UNDECIDED |
| L9 | Hız sınırlama ve kimlik uygulanmadı | [ADR-008](../adr/008-jwt-auth-with-opaque-refresh.md) karar olarak var, kod olarak yok; bugün yazma ucu da olmadığı için yüzey küçük |

## 5. Bilinen sapmalar (kod ↔ ADR/spec)

Bilinen sapma yok. Uygulama sırasında ortaya çıkan ve **karara bağlanan** farklar ADR'lere ve `openspec/changes/add-storefront-product-listing/tasks.md` içindeki S1–S11 tablosuna yazıldı; bu yüzden UNDECIDED bir sapma kalmadı.

Kural (`AGENTS.md` §5.4): kod ile ADR birbirine aykırıysa bu bir **sapma**dır; asistan hiçbirini sessizce değiştirmez, önce raporlar ve karar yoksa madde **UNDECIDED** olarak bu tabloya yazılır.

| # | Sapma | Durum |
|---|---|---|
| - | - | - |

## 6. Sıradaki adım

1. Vitrinin iki ekran genişliği ve iki temada gözle doğrulanması (L3, insan işi).
2. Sonraki dilim adayı: [ADR-008](../adr/008-jwt-auth-with-opaque-refresh.md)'in uygulanması (kayıt, e-posta doğrulama, giriş, token yenileme) — en çok şeyin üzerine oturduğu katman.

# Güncel Durum ve Bilinen Sınırlamalar

**Son doğrulama:** 2026-10-06

> **Bu dosya projenin gerçeğidir.** Sunum, yol haritası, ürün belgesi veya herhangi bir planlama dokümanı "şu an ne var?" sorusuna cevap olarak kullanılamaz (`AGENTS.md` §5.4). Şüphede **kod** ve bu dosya esastır.
> Bir katmanın durumu, bilinen bir sınırlama veya sapma değiştiğinde bu dosya **aynı commit'te** güncellenir (`AGENTS.md` §6.4).

---

## 1. Tek cümlelik özet

Depoda bugün **kural ve dokümantasyon apparatus'u** var; **uygulama kodu yoktur.** `api/`, `web/` ve `shared/` dizinleri henüz oluşturulmadı. İlk dikey dilim, karar bekleyen iki teknik maddeye bağlıdır (`openspec/KALANLAR.md` B1 ve B2).

## 2. Katman durumu

| Katman | Durum | Kanıt |
|---|---|---|
| `AGENTS.md` (anayasa) | ✅ Yazıldı, yürürlükte | Depo kökü, 460 satır |
| `docs/adr/` | ✅ ADR-001…009 Accepted | [ADR index](../adr/index.md) |
| `docs/` (Diátaxis) | ✅ İskelet + şablonlar + bir how-to | `docs/` ağacı |
| `openspec/` | ✅ Çalışma alanı + ilk change **Proposed** | `openspec/changes/add-storefront-product-listing/` (proposal, design, tasks, delta spec); ana `specs/` henüz boş (sync uygulama sonrası) |
| `.claude/` (opsx komutları + skill'ler) | ✅ Kurulu | `.claude/commands/opsx/`, `.claude/skills/` |
| `shared/contracts/openapi.yaml` | ❌ Yok | Kaynak ve üretim biçimi [ADR-009](../adr/009-contract-generated-from-spring.md) ile karara bağlandı (Spring'den üretilir); dosya ilk dilimde üretilecek |
| `api/` (Spring Boot) | ❌ Yok | Dizin oluşturulmadı; `add-storefront-product-listing` Faz 1–3 kapsamında |
| `web/` (Next.js) | ❌ Yok | Dizin oluşturulmadı; `add-storefront-product-listing` Faz 4 kapsamında |
| `docker-compose.yml` | ❌ Yok | `add-storefront-product-listing` Faz 1.1 |
| `.githooks/pre-commit` | ✅ Devrede | `git config core.hooksPath .githooks` çalıştırıldı |
| `.github/workflows/ci.yml` | ✅ Koştu, **4/4 yeşil** | Run `37491677164`: `api` ve `web` job'ları kod olmadığı için atlandı; `docs` strict derleme ve K6/K10 kural denetimi geçti |
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
| `openspec` CLI | ❌ Kurulu değil | `.claude/skills/` ve `/opsx:*` komutları CLI'ı çağırır; CLI kurulana kadar bu komutlar çalışmaz |

## 4. Bilinen sınırlamalar

| # | Sınırlama | Etki |
|---|---|---|
| L1 | Uygulama kodu yok | Hiçbir uçtan uca akış çalışmıyor; `AGENTS.md` §7 komutlarının hiçbiri bugün koşulamaz |
| L2 | `openspec` CLI kurulu değil | `/opsx:propose` ve diğer komutlar CLI gelene kadar çalışmaz; change'ler elle de yazılabilir (`AGENTS.md` §5.2 artefakt kuralları geçerli) |
| L3 | Portal yalnızca yerelde derlendi | Strict derleme yerel makinede temiz; CI üzerinde (Linux, Python 3.12) henüz koşmadı |
| L4 | CI yalnızca kodsuz yolda doğrulandı | 4/4 yeşil, ama `api` ve `web` job'ları atlandı: Maven ve npm adımlarının gerçekten çalıştığı ilk dilimde görülecek |
| L5 | `pre-commit` hook'u yalnızca bu makinede devrede | `core.hooksPath` klon başına ayarlanır; yeni klonda tekrar çalıştırılmalı. Bootstrap commit'i `--no-verify` ile atıldı (dosyalar commit'lenmeden `git-revision` eklentisi strict derlemeyi kırıyordu) |
| L6 | İlk dilim onay bekliyor | B1 ve B2 karara bağlandı ([ADR-008](../adr/008-jwt-auth-with-opaque-refresh.md), [ADR-009](../adr/009-contract-generated-from-spring.md)); `api/` ve `web/` iskeleti `add-storefront-product-listing` artefaktları onaylanınca yazılır (`AGENTS.md` §5.3) |
| L7 | Ticari kararlar sonraki dilimleri blokluyor | Ödeme (A1), kargo (A2), satıcı onboarding (A3), MVP kapsamı (A4), fotoğraf depolama (B3), bildirim kanalı (B4), barındırma (B5) hâlâ UNDECIDED |

## 5. Bilinen sapmalar (kod ↔ ADR/spec)

Yok: kod olmadığı için sapma da yok. Bu tablo kod yazıldıktan sonra anlam kazanacaktır.

Kural (`AGENTS.md` §5.4): kod ile ADR birbirine aykırıysa bu bir **sapma**dır; asistan hiçbirini sessizce değiştirmez, önce raporlar ve karar yoksa madde **UNDECIDED** olarak bu tabloya yazılır.

| # | Sapma | Durum |
|---|---|---|
| - | - | - |

## 6. Sıradaki adım

1. `add-storefront-product-listing` change artefaktları **kullanıcı onayı** bekliyor (`AGENTS.md` §5.3: propose yalnızca planlamadır).
2. Onay sonrası uygulama `tasks.md` faz sırasıyla yürür: ortam ve `api` iskeleti → veri modeli → uç nokta ve sözleşme → `web` vitrin ekranı → doküman ve kapanış.
3. Dilim bittiğinde D1–D6 kararları ADR-010… olarak yazılır (archive ön koşulu) ve bu dosya güncellenir.
4. Sonraki dilim adayları: kimlik ve oturum ([ADR-008](../adr/008-jwt-auth-with-opaque-refresh.md) uygulaması), ürün detay sayfası, arama ve filtre.

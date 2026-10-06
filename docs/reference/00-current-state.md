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
| `docs/adr/` | ✅ ADR-001…007 Accepted | [ADR index](../adr/index.md) |
| `docs/` (Diátaxis) | ✅ İskelet + şablonlar + bir how-to | `docs/` ağacı |
| `openspec/` | ✅ Çalışma alanı + `config.yaml` + `KALANLAR.md` | `openspec/`; **henüz hiç change açılmadı**, `specs/` boş |
| `.claude/` (opsx komutları + skill'ler) | ✅ Kurulu | `.claude/commands/opsx/`, `.claude/skills/` |
| `shared/contracts/openapi.yaml` | ❌ Yok | Sözleşme biçimi [ADR-003](../adr/003-openapi-as-single-contract.md) ile karara bağlı, dosya yazılmadı |
| `api/` (Spring Boot) | ❌ Yok | Dizin oluşturulmadı |
| `web/` (Next.js) | ❌ Yok | Dizin oluşturulmadı |
| `docker-compose.yml` | ❌ Yok | Servisler ilk dilimde tanımlanacak |
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
| L6 | Açık kararlar ilk dilimi blokluyor | Kimlik doğrulama (B1) ve sözleşme kaynağı (B2) kararı olmadan `api/` ve `web/` iskeleti yazılmıyor (`AGENTS.md` §0, §15) |

## 5. Bilinen sapmalar (kod ↔ ADR/spec)

Yok: kod olmadığı için sapma da yok. Bu tablo kod yazıldıktan sonra anlam kazanacaktır.

Kural (`AGENTS.md` §5.4): kod ile ADR birbirine aykırıysa bu bir **sapma**dır; asistan hiçbirini sessizce değiştirmez, önce raporlar ve karar yoksa madde **UNDECIDED** olarak bu tabloya yazılır.

| # | Sapma | Durum |
|---|---|---|
| - | - | - |

## 6. Sıradaki adım

1. `openspec/KALANLAR.md` **B1** (kimlik doğrulama modeli) ve **B2** (sözleşme kaynağı) karara bağlanır → ADR yazılır.
2. İlk dikey dilim için change açılır (`/opsx:propose`, CLI kurulduktan sonra): hesap + oturum akışı, uçtan uca (sözleşme → migration → entity → service → controller → üretilen tipler → `web` ekranı → test).
3. Dilimle birlikte `docker-compose.yml`, `api/pom.xml`, `web/package.json` ve ilgili how-to sayfaları yazılır; bu dosya güncellenir.

# Genel: Feature'a Bağlı Olmayan İşler

**Son değişiklik:** 2026-10-06

Dokümantasyon altyapısı, geliştirme araçları, AI kural dosyaları, CI ve tek bir feature'a bağlanamayan altyapı işleri burada kayıt altına alınır. Bir feature'a ait değişiklik buraya **yazılmaz**; ilgili feature dosyasına gider.

---

## Kapsam

| Alan | Nerede yaşar |
|---|---|
| AI bağlam yönetimi | `AGENTS.md` (tek doğruluk kaynağı; ayrı `CLAUDE.md` / `.cursorrules` adaptörü tutulmaz), `.claude/commands/opsx/`, `.claude/skills/` |
| Spec-driven change aracı | `openspec/` (`config.yaml`, `specs/`, `changes/`, `KALANLAR.md`) |
| Mimari kararlar | [`docs/adr/`](../adr/index.md) |
| Dokümantasyon portalı | `docs/.tools/` (MkDocs), [docs/README.md](../README.md) |
| Sözleşme | `shared/contracts/openapi.yaml` ([ADR-003](../adr/003-openapi-as-single-contract.md)) |
| Geliştirme ortamı | `docker-compose.yml`, `.githooks/pre-commit`, `.github/workflows/` |
| Yerel kurulum | [Yerel geliştirme ortamı](../how-to/yerel-gelistirme.md) |

## Açık konular

- **Kod henüz yazılmadı.** `api/`, `web/` ve `shared/` dizinleri boş; ilk dikey dilim `openspec/KALANLAR.md` B1 (kimlik doğrulama modeli) ve B2 (sözleşme kaynağı) kararlarına bağlı.
- `docs/.tools/` MkDocs portalı kuruldu ve `mkdocs build --strict` çalıştırıldı: ilk koşuda 18 uyarı (docs dışına verilen markdown linkler ve git-revision eklentisi) düzeltildi, derleme temiz.
- `.githooks/pre-commit` yazıldı ve `git config core.hooksPath .githooks` ile devreye alındı.
- `.github/workflows/ci.yml` ilk kez koştu ve 4/4 yeşil (run `37491677164`); `api` ve `web` job'ları kod olmadığı için atlandı, gerçek derleme/test yolu ilk dilimde doğrulanacak.
- A1–A4 ve B1–B5 maddeleri ekip/ticari karar bekliyor; tam liste `openspec/KALANLAR.md` dosyasında.

## Tarihçe

- 2026-10-06: K6 taramasının kapsamı düzeltildi (CI + `pre-commit`): depo kökü yerine yalnızca kaynak dizinleri (`api`, `web/src`, `shared`) taranıyor. Kök tarama kaçınılmaz yanlış pozitif üretiyordu: kural dosyaları bu kelimeleri kural metni olarak içeriyor, CI iş akışı ve hook ise desenlerin kendisini barındırıyor (ikisi de her koşuda kırmızı olurdu). K10 dosya adı taraması bilinçli olarak depo genelinde kaldı. MkDocs `exclude_docs` listesine `.tools/` eklendi: portalın kendi venv dosyaları tarama gürültüsü yapıyordu (site çıktısına girmiyordu).
- 2026-10-06: AI kural ve dokümantasyon apparatus'u kuruldu: `AGENTS.md` (monorepo anayasası, K1–K10), `.claude/commands/opsx/` ve `.claude/skills/` (spec-driven change araçları), `openspec/` çalışma alanı + `config.yaml` + `KALANLAR.md` (10 açık karar), Diátaxis `docs/` iskeleti ve şablonları, ADR-001…007 (iki birim, bağımlılık bütçesi, OpenAPI sözleşmesi, PostgreSQL + Flyway, test stratejisi, para temsili, satıcı izolasyonu), `docs/reference/00-current-state.md`, yerel geliştirme rehberi, MkDocs portalı, `pre-commit` hook'u, CI iş akışı ve kök `README.md`.
- 2026-10-06: Depo açıldı: `git init`, `main` branch, `.gitignore` (secret/üretilmiş dosya/build çıktısı kuralları) ve GitHub'da private repo (`sertacozmen1/bahceden`).
- 2026-10-06: Yerel geliştirme ortamı hazırlandı: JDK 21 LTS (Corretto 21.0.12.1) `~/.jdks` altına kuruldu; Maven için IntelliJ'in bundle'ı (3.9.16) kullanılacağı belirlendi, ayrı kurulum yapılmadı; Docker Engine 29.6.2 ve Node 24 doğrulandı.

# Bahçeden

Bahçeden, meyve-sebze üreticisini son tüketiciyle **doğrudan** buluşturan çok satıcılı bir pazaryeridir. Manisa'daki bir üretici, bir hırdavatçının büyük pazaryerlerine kaydolduğu kolaylıkla kaydolur, ürününü listeler ve **kargo ile** gönderir. Hal, komisyoncu ve aradaki fiyat katmanları zincirden çıkar.

Bu monorepo iki dağıtılabilir birim barındırır: **`api/`** (Spring Boot 3 REST API, tek yazma otoritesi) ve **`web/`** (Next.js, alıcı vitrini + satıcı paneli). İkisi arasındaki tek sözleşme `shared/contracts/openapi.yaml` dosyasıdır ([ADR-001](docs/adr/001-spring-api-and-next-web.md), [ADR-003](docs/adr/003-openapi-as-single-contract.md)).

> [!IMPORTANT]
> **Depo bugün uygulama kodu içermiyor.** Mevcut durum, tamamlanmış işler ve bilinen sınırlamalar için her zaman projenin gerçeği olan [docs/reference/00-current-state.md](docs/reference/00-current-state.md) dosyasını referans alın. Yol haritası ve ürün belgeleri bu soruya cevap olarak kullanılamaz.

---

## Nereden başlamalı?

| Sıra | Dosya | Ne verir |
|---|---|---|
| 1 | [docs/reference/00-current-state.md](docs/reference/00-current-state.md) | Şu an gerçekten ne var, ne yok |
| 2 | [AGENTS.md](AGENTS.md) | Monorepo anayasası: K1–K10 kuralları, akış, yasaklar. **İnsan ve AI için bağlayıcı.** |
| 3 | [docs/adr/index.md](docs/adr/index.md) | Teknoloji ve mimari kararlar, gerekçeleriyle |
| 4 | [docs/how-to/yerel-gelistirme.md](docs/how-to/yerel-gelistirme.md) | Makineyi çalışır hale getirme |
| 5 | [openspec/KALANLAR.md](openspec/KALANLAR.md) | Karar bekleyen konular (ilk dilimi bunlar blokluyor) |

## Depo yapısı

```text
bahceden/
├── AGENTS.md              ← Anayasa: tüm AI asistanları ve geliştiriciler için tek doğruluk kaynağı
├── api/                   ← Spring Boot 3 REST API (Java 21, Maven)          [henüz yok]
├── web/                   ← Next.js (App Router, TypeScript)                  [henüz yok]
├── shared/contracts/      ← openapi.yaml: iki birim arasındaki tek sözleşme   [henüz yok]
├── docs/                  ← Diátaxis dokümantasyon + adr/ + features/
├── openspec/              ← Spec-driven change çalışma alanı + KALANLAR.md
├── .claude/               ← /opsx:* komutları ve openspec skill'leri
├── .githooks/pre-commit   ← Katmana göre doğrulama + K6/K10 taraması
└── .github/workflows/     ← CI: api testleri, web lint/build, docs strict, kural denetimi
```

## Hızlı başlangıç

```bash
git clone https://github.com/sertacozmen1/bahceden.git
cd bahceden
git config core.hooksPath .githooks     # commit öncesi doğrulama (klon başına bir kez)
```

Araç kurulumu (JDK 21, Maven, Node, Docker) için: [Yerel geliştirme ortamını kurma](docs/how-to/yerel-gelistirme.md).

Dokümantasyon portalı:

```bash
./docs/.tools/serve.sh                  # http://localhost:8888
```

Uygulamayı çalıştırma (`docker compose up`) ilk dikey dilimle birlikte gelecek; bugün `docker-compose.yml` yok.

## Çalışma biçimi

- **Önce doküman.** Yeni özellik, davranış değişikliği veya mimari karar koddan değil dokümandan başlar (`AGENTS.md` K1). Spec'i değiştiren işler OpenSpec change ile planlanır; küçük işler bile [`docs/features/`](docs/features/index.md) *Tarihçe* satırı bırakır.
- **Önce sözleşme.** İstemciye görünen her değişiklik `shared/contracts/openapi.yaml` ile başlar; `web` tiplerini üretir, elle yazmaz (`AGENTS.md` K2, [ADR-003](docs/adr/003-openapi-as-single-contract.md)).
- **Dikey dilim.** İş, uçtan uca çalışan tek bir akış olarak kesilir: sözleşme → migration → entity → service → controller → üretilen tipler → ekran → test (`AGENTS.md` K5).
- **Bağımlılık bütçesi var.** Bütçe dışına paket eklemek onay ve ADR gerektirir ([ADR-002](docs/adr/002-dependency-budget.md)).
- **Secret bağlama girmez.** `.env`, gerçek müşteri verisi, ödeme ve kargo anahtarları ne commit'e ne AI bağlamına girer (`AGENTS.md` K10).

## Branch ve commit

- Branch: `<TICKET>-kisa-aciklama`, `hotfix/…` veya `docs/…`. `main`'e doğrudan commit yok.
- Commit mesajı İngilizce, emir kipi: `type(scope): summary` — `type` ∈ {feat, fix, docs, refactor, test, chore, ci}, `scope` ∈ {api, web, shared, docs, adr, infra}.

Ayrıntı: `AGENTS.md` §13.

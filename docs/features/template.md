# [Feature Adı]

**Durum:** Fikir | Geliştirmede | Yayında | Kaldırıldı
**İlk sürüm:** YYYY-MM-DD
**Son değişiklik:** YYYY-MM-DD

| | |
|---|---|
| **Ürün belgesi** | [product/…](../product/index.md) veya `-` |
| **Kararlar** | [ADR-NNN](../adr/index.md), … |
| **Spec (capability)** | `openspec/specs/<capability>` , … |
| **Change'ler** | `openspec/changes/archive/YYYY-MM-DD-<ad>` , … |
| **Rehberler** | [how-to/…](../how-to/index.md) |
| **Kod** | `api/src/main/java/com/bahceden/<domain>/`, `web/src/features/<domain>/` |

---

## Ne yapar

Kullanıcı diliyle 3–6 cümle: hangi problemi çözer, kullanıcı ne görür/yapar. Gereksinim listesi değil; o `openspec/specs/` altındadır.

## Bilinçli sınırlar

Kapsam dışı bırakılanlar ve nedenleri (varsa ADR linki).

## Açık konular

`openspec/KALANLAR.md` maddeleri veya bekleyen dış işler. Karara bağlanınca buradan silinir ve Tarihçe'ye satır düşer.

## Tarihçe

Yeni en üstte. Append-only; eski satır düzenlenmez.

- YYYY-MM-DD: Ne değişti (ADR-NNN / change adı / PR #n)
- YYYY-MM-DD: İlk sürüm (change adı)

---

> [!NOTE]
> Bu dosya bir şablondur. Yeni feature için kopyalayın, `docs/features/index.md` tablosuna ve `docs/.tools/mkdocs.yml` nav'ına ekleyin.

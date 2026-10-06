# [ADR-001] İki dağıtılabilir birim: Spring Boot REST API + Next.js web uygulaması

**Tarih:** 2026-10-06
**Durum:** Accepted

---

## Context

Bahçeden'in iki farklı kullanıcı yüzü var: alıcının gördüğü **vitrin** (arama motorunda bulunması gereken, hızlı açılması gereken ürün ve kategori sayfaları) ve satıcının kullandığı **panel** (form ağırlıklı, oturum arkasında, sahada telefonla kullanılacak).

Kararın alındığı andaki kısıtlar:

- Ekip küçük; MVP ölçeği tek bölge ve az sayıda satıcı (AGENTS.md §3).
- Vitrinin SEO'ya ihtiyacı var: ürün sayfaları arama motorundan gelmeli. Tamamen istemcide render edilen bir SPA bu ihtiyacı ek iş olmadan karşılamıyor.
- İş kuralları (fiyat, stok, sipariş durumu, komisyon) ve kişisel veri tek bir güvenilir yerde olmalı; tarayıcıya güvenilemez.
- Ekibin Java/Spring ve React/Next.js deneyimi var.

## Decision

İki dağıtılabilir birim kurulur:

1. **`api/`** — Spring Boot 3 (Java 21, Maven) REST API. Domain modeli, iş kuralları, persistence, kimlik doğrulama ve yetkilendirme burada yaşar. **Tek yazma otoritesidir.**
2. **`web/`** — Next.js (App Router, TypeScript). Alıcı vitrini **ve** satıcı paneli. Yalnızca HTTP ile `api/`'ye konuşur; veritabanına ve dış servislere doğrudan erişmez.

İkisi arasındaki tek sözleşme `shared/contracts/openapi.yaml` dosyasıdır ([ADR-003](003-openapi-as-single-contract.md)). Birbirlerini import etmezler; ortak kod modülü (`common/`, `core-lib/`) açılmaz.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Tek uygulama: Spring + Thymeleaf sunucu tarafı render | Satıcı panelindeki etkileşim yoğunluğu (çok adımlı ürün formu, fotoğraf yükleme, sepet) Thymeleaf ve elle yazılmış JS ile iki katı işe dönüşürdü; ekibin React deneyimi boşa giderdi. |
| Next.js tek başına (API routes + ORM ile DB erişimi) | İş kuralları ve kişisel veri Node katmanına taşınırdı; para/stok/sipariş gibi işlemsel mantık için Spring'in transaction ve doğrulama olgunluğu tercih edildi. Ekibin asıl gücü de Java tarafında. |
| Spring + React SPA (Vite, istemci tarafı render) | Vitrinin SEO ihtiyacı için ayrıca SSR/prerender altyapısı kurmak gerekirdi; Next.js bunu kutudan veriyor. |
| Mikroservisler (catalog, order, shipping ayrı servis) | MVP ölçeği için fazlasıyla ağır: dağıtık transaction, servis keşfi, ayrı dağıtım hattı. AGENTS.md §3 over-engineering yasağına aykırı. |

## Consequences

### Olumlu
- Vitrin SSR/ISR ile SEO uyumlu; satıcı paneli zengin etkileşim yazabiliyor.
- İş kuralları tek yerde: `api/` dışında hiçbir yerde fiyat veya stok kararı verilmiyor.
- İki birim ayrı ayrı ölçeklenebilir ve ayrı ayrı dağıtılabilir.

### Olumsuz
- İki dil, iki paket yöneticisi, iki test koşucusu, iki CI işi: bakım yüzeyi tek uygulamaya göre büyük.
- Her yeni alan iki yerde iş demek (DTO + üretilen tip + form); sözleşme senkronu disiplin gerektiriyor ([ADR-003](003-openapi-as-single-contract.md) bunu zorunlu kılar).
- Yerel geliştirme için Docker Compose pratik zorunluluk (db + api + web + mailpit).

### Nötr / Trade-off
- Ortak kod paylaşımı bilinçli olarak yok; küçük yardımcıların (para biçimlendirme gibi) iki tarafta ayrı yaşaması kabul edilmiş maliyettir. Paylaşım ihtiyacı büyürse bu ADR yeniden değerlendirilir, üçüncü bir modül sessizce açılmaz.

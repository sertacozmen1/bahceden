# [ADR-002] Bağımlılık bütçesi: Spring starters, Flyway, Next.js çekirdeği

**Tarih:** 2026-10-06
**Durum:** Accepted

---

## Context

Bağımlılık eklemek ucuz görünür, pahalı çıkar: transitive paketler, güvenlik yüzeyi, sürüm yükseltme borcu, derleme süresi, imaj boyutu ve "bu paket neden burada?" sorusunun cevabının zamanla kaybolması. Küçük bir ekibin MVP'sinde bu maliyet doğrudan ürün hızından düşer.

Aynı zamanda sıfır bağımlılık da gerçekçi değil: kimlik doğrulama, migration ve ORM'i elle yazmak daha büyük bir hata olurdu. İhtiyaç, bir **bütçe** ve bütçeyi aşmanın açık bir prosedürü.

## Decision

Çalışma zamanı ve geliştirme bağımlılıkları aşağıdaki bütçeyle **sınırlıdır**:

| Katman | İzinli |
|---|---|
| `api/` runtime | `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `postgresql` sürücüsü, `flyway-core` |
| `api/` test | `spring-boot-starter-test` (JUnit 5 + AssertJ + MockMvc), `spring-security-test`, Testcontainers (PostgreSQL) |
| `web/` runtime | `next`, `react`, `react-dom` |
| `web/` dev | `typescript`, `eslint` + `eslint-config-next`, `vitest` + `@testing-library/react`, `@playwright/test`, OpenAPI'den TypeScript tip üreteci |
| Sistem | Java 21 (LTS), PostgreSQL 17, Node 20+, Docker Compose, `mailpit` |

Bu listenin dışındaki her paket, servis ve Docker imajı **mimari karardır**: eklemeden önce AGENTS.md K7'deki dört soru cevaplanır (platform neden yetmiyor, iki alternatif ve ret gerekçesi, yük analizi, hangi katman) ve onay alınırsa ADR yazılır. Sürümler pin'lenir (Maven'da açık sürüm veya BOM, npm'de tam sürüm).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Serbest bağımlılık politikası ("gerekirse ekle") | Bütçe olmadan bağımlılık listesi aylar içinde tanınmaz hale gelir ve her paket sessiz bir bakım borcu bırakır. |
| Lombok | Java 21 `record`'ları DTO'lar için yeterli; entity'lerde elle yazılmış erişimciler okunabilirliği bozmuyor. Annotation processor derleme ve IDE karmaşıklığı getiriyor. |
| MapStruct | Mapper sayısı MVP'de az; elle yazılmış mapper daha az sihir ve daha anlaşılır hata mesajı veriyor. Mapper sayısı ciddi artarsa yeniden değerlendirilir. |
| Tailwind CSS veya UI kütüphanesi (MUI, shadcn/ui) | Tasarım yüzeyi MVP'de küçük: CSS Modules ve token dosyası yetiyor. UI kütüphanesi hem tasarım dilini hem yükseltme borcunu dışarıdan ithal ediyor. |
| Redux / Zustand / TanStack Query | Veri çekme büyük ölçüde sunucu tarafında (Server Component); istemci durumu formlarla sınırlı. React'in kendi durumu yetiyor. |
| H2 ile test | Üretim PostgreSQL ise test de PostgreSQL olmalı; H2 farkı (tip davranışı, kilitleme, `jsonb`) testi yalancı yeşile çeviriyor. Testcontainers bu yüzden bütçeye alındı ([ADR-005](005-test-strategy.md)). |

## Consequences

### Olumlu
- Küçük ve okunabilir `pom.xml` ile `package.json`; hızlı derleme, küçük imaj.
- Her bağımlılığın bir gerekçesi var; "bu neden burada?" sorusu cevaplanabiliyor.
- Güvenlik yüzeyi ve yükseltme borcu sınırlı.

### Olumsuz
- Bazı işler elle yazılıyor (mapper, küçük yardımcılar, stil sistemi); kısa vadede daha fazla kod.
- Yeni paket ihtiyacı akışı yavaşlatıyor: onay ve ADR adımı var.

### Nötr / Trade-off
- Bütçe dondurulmuş bir liste değil; ihtiyaç kanıtlandığında ADR ile büyür. Yasak olan **sessizce** büyümesidir.

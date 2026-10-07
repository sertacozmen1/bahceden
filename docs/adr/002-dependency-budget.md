# [ADR-002] Bağımlılık bütçesi: Spring starters, Liquibase, Next.js çekirdeği

**Tarih:** 2026-10-06
**Durum:** Accepted · bütçe [ADR-009](009-contract-generated-from-spring.md) ile bir kalem genişletildi (2026-10-06) · `web/` bütçesi ve stil yöntemi [ADR-027](027-shadcn-ui-design-system.md), çeviri paketi [ADR-029](029-next-intl-without-routing.md) ile değişti (2026-10-07)

---

> [!NOTE]
> **Bütçe değişiklikleri** (ADR superseded edilmeden, "ihtiyaç kanıtlandığında ADR ile büyür" maddesi işletilerek):
>
> | Tarih | Eklenen | Karar |
> |---|---|---|
> | 2026-10-06 | `api/` runtime: `springdoc-openapi-starter-webmvc-api` | [ADR-009](009-contract-generated-from-spring.md) |
> | 2026-10-06 | `api/` runtime: `spring-boot-starter-oauth2-resource-server` (JWT; Nimbus JOSE dahil) | [ADR-008](008-jwt-auth-with-opaque-refresh.md) §6 |
> | 2026-10-06 | **Değişim:** `flyway-core` + `flyway-database-postgresql` → `liquibase-core` | [ADR-016](016-liquibase-for-schema-migrations.md) |
> | 2026-10-06 | `api/` runtime: `spring-boot-starter-mail` (SMTP; doğrulama bağlantısı ve ikinci faktör kodu). K7'nin dört sorusu `openspec/changes/archive/2026-10-07-add-account-and-session/proposal.md` içinde cevaplandı; kullanıcı onayı alındı. Sağlayıcı seçimi yapılmadı: SMTP sağlayıcıdan bağımsızdır, geliştirmede `mailpit` | [ADR-017](017-email-verification-non-blocking-and-2fa-switch.md) |
> | 2026-10-07 | `web/` runtime: `@base-ui/react`, `class-variance-authority`, `clsx`, `tailwind-merge`, `lucide-react`; `web/` dev: `tailwindcss`, `@tailwindcss/postcss`, `tw-animate-css`. Aşağıdaki "Tailwind CSS veya UI kütüphanesi" reddi bu kapsamda kalktı: CSS Modules yerine Tailwind v4 + shadcn/ui. K7'nin dört sorusu cevaplandı, kullanıcı onayı alındı | [ADR-027](027-shadcn-ui-design-system.md) |
> | 2026-10-07 | `web/` runtime: `next-intl` (çeviri, yönlendirmesiz). K7'nin dört sorusu `openspec/changes/archive/2026-10-07-add-i18n/proposal.md` içinde cevaplandı; proje sahibi "sektör standardı" seçeneğini onayladı. `createNextIntlPlugin` kullanılmaz | [ADR-029](029-next-intl-without-routing.md) |

## Context

Bağımlılık eklemek ucuz görünür, pahalı çıkar: transitive paketler, güvenlik yüzeyi, sürüm yükseltme borcu, derleme süresi, imaj boyutu ve "bu paket neden burada?" sorusunun cevabının zamanla kaybolması. Küçük bir ekibin MVP'sinde bu maliyet doğrudan ürün hızından düşer.

Aynı zamanda sıfır bağımlılık da gerçekçi değil: kimlik doğrulama, migration ve ORM'i elle yazmak daha büyük bir hata olurdu. İhtiyaç, bir **bütçe** ve bütçeyi aşmanın açık bir prosedürü.

## Decision

Çalışma zamanı ve geliştirme bağımlılıkları aşağıdaki bütçeyle **sınırlıdır**:

| Katman | İzinli |
|---|---|
| `api/` runtime | `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `postgresql` sürücüsü, `liquibase-core` |
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

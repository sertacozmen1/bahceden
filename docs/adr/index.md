# Architecture Decision Records (ADR)

> **Biçim:** [Michael Nygard'ın ADR formatı](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)

## ADR nedir?

Architecture Decision Records, projedeki önemli mimari kararları ve bu kararların arkasındaki gerekçeleri belgeleyen kısa, yapılandırılmış dokümanlardır.

**Neden ADR tutuyoruz?**

- Yeni bir geliştirici "Neden GraphQL yerine OpenAPI kullanıyoruz?" diye sorduğunda cevap kodda değil ADR dosyasında bulunur.
- Kararın bağlamı (o andaki kısıtlar, değerlendirilen alternatifler) zamanla unutulur; ADR bunu önler.
- Kararı revize etmek gerektiğinde orijinal gerekçe ve kısıtlar bilinir: neyin değiştiğini görmek kolaylaşır.

**Bağlayıcılık:** Kabul edilmiş (`Accepted`) bir ADR, `AGENTS.md` dahil her şeyin üstündedir. Bir ADR'ye aykırı davranmak için önce o ADR'yi *superseded* eden yeni bir ADR yazılır ve kullanıcı onayı alınır (`AGENTS.md` §9).

---

## ADR listesi

| # | Başlık | Durum | Tarih |
|---|--------|-------|-------|
| 001 | [İki dağıtılabilir birim: Spring Boot REST API + Next.js web](001-spring-api-and-next-web.md) | Accepted | 2026-10-06 |
| 002 | [Bağımlılık bütçesi: Spring starters, Liquibase, Next.js çekirdeği](002-dependency-budget.md) | Accepted · `web/` bütçesi [027](027-shadcn-ui-design-system.md) ile değişti | 2026-10-06 |
| 003 | [`api/` ile `web/` arasındaki tek sözleşme OpenAPI dosyasıdır](003-openapi-as-single-contract.md) | Accepted | 2026-10-06 |
| 004 | [PostgreSQL ve şema değişikliği yalnızca Flyway ile](004-postgresql-with-flyway.md) | Superseded by [016](016-liquibase-for-schema-migrations.md) | 2026-10-06 |
| 005 | [Test stratejisi: Testcontainers, `critical` etiketi, sınırlı tarayıcı testi](005-test-strategy.md) | Accepted | 2026-10-06 |
| 006 | [Para minor unit tamsayı olarak taşınır; hesap sunucuda](006-money-as-minor-unit.md) | Accepted | 2026-10-06 |
| 007 | [Satıcı veri izolasyonu sorgu seviyesinde; varsayılan deny](007-query-level-ownership-scoping.md) | Accepted | 2026-10-06 |
| 008 | [Kimlik: JWT erişim + iptal edilebilir yenileme token'ı, tek hesap tablosu](008-jwt-auth-with-opaque-refresh.md) | Accepted · §4 [017](017-email-verification-non-blocking-and-2fa-switch.md) ile değişti | 2026-10-06 |
| 009 | [Sözleşme Spring kodundan üretilir; `openapi.yaml` commit'lenen çıktıdır](009-contract-generated-from-spring.md) | Accepted | 2026-10-06 |
| 010 | [Ürün yayın durumu enum'dur, boolean bayrak değil](010-product-status-as-enum.md) | Accepted | 2026-10-06 |
| 011 | [Liste sorguları fetch join; sorgu sayısı testle sabit](011-fetch-join-with-query-count-test.md) | Accepted | 2026-10-06 |
| 012 | [Sayfa boyutu üst sınırı sunucuda; aşan istek sınıra çekilir](012-page-size-capped-on-server.md) | Accepted | 2026-10-06 |
| 013 | [`sellers` tablosu onboarding kararından önce minimum doğar](013-minimal-seller-table-ahead-of-onboarding.md) | Accepted | 2026-10-06 |
| 014 | [Geliştirme verisi dev profilli tekrarlanabilir migration ile](014-dev-seed-via-profiled-repeatable-migration.md) | Accepted | 2026-10-06 |
| 015 | [Sözleşme tazeliği testle zorunlu; dosya testten üretilir](015-contract-freshness-enforced-by-test.md) | Accepted | 2026-10-06 |
| 016 | [Şema değişiklikleri Liquibase ile yönetilir](016-liquibase-for-schema-migrations.md) | Accepted | 2026-10-06 |
| 017 | [E-posta doğrulaması girişi engellemez; 2FA hesap anahtarıyla açılır](017-email-verification-non-blocking-and-2fa-switch.md) | Accepted | 2026-10-06 |
| 018 | [Yenileme token'ı: rotasyon ve tekrar kullanımda aile iptali](018-refresh-token-rotation-with-family-revocation.md) | Accepted | 2026-10-07 |
| 019 | [E-posta SMTP üzerinden, sağlayıcıdan bağımsız, akışı kesmeden](019-email-via-smtp-provider-agnostic.md) | Accepted | 2026-10-07 |
| 020 | [Tek kullanımlık token'lar: 256 bit, SHA-256 hash'li, süreli](020-verification-tokens-sha256-single-use.md) | Accepted | 2026-10-07 |
| 021 | [Kimlik yanıtları hesabın varlığını açığa çıkarmaz](021-identity-responses-do-not-reveal-accounts.md) | Accepted | 2026-10-07 |
| 022 | [Kimlik uçlarında bellek içi sabit pencere hız sınırı](022-in-memory-fixed-window-rate-limiting.md) | Accepted | 2026-10-07 |
| 023 | [`web` BFF: HttpOnly çerezler, başlık tabanlı CSRF, yönlendirmeyle yenileme](023-web-bff-cookies-and-header-csrf.md) | Accepted | 2026-10-07 |
| 024 | [Yetkilendirme JWT iddialarıyla; `/accounts/me` taze okunur](024-roles-in-jwt-claims-fresh-me-endpoint.md) | Accepted | 2026-10-07 |
| 025 | [İkinci faktör kodu hesaba bağlı; ikinci adım e-posta + kod](025-second-factor-code-bound-to-account.md) | Accepted | 2026-10-07 |
| 026 | [Doğrulama bağlantısını yeniden gönderme oturum ister](026-verification-link-resend-requires-session.md) | Accepted | 2026-10-07 |
| 027 | [Tasarım sistemi: shadcn/ui (Base UI) + Tailwind v4](027-shadcn-ui-design-system.md) | Accepted | 2026-10-07 |

**Sıradaki numara: 028.**

**Durum değerleri:** `Proposed` (öneri, henüz bağlayıcı değil) · `Accepted` (yürürlükte, bağlayıcı) · `Deprecated` (artık geçerli değil, yerine bir şey gelmedi) · `Superseded by [ADR-XXX]` (yerini başka bir karara bıraktı).

---

## Karar bekleyen konular

Aşağıdaki konular henüz karara bağlanmadı ve ilgili alanda kod yazılmasını **bloke eder**. Tam liste ve gerekçeler: `openspec/KALANLAR.md` ve `AGENTS.md` §15.

| Konu | Kaynak madde | Karara bağlanınca |
|---|---|---|
| Ödeme sağlayıcısı, payout modeli, komisyon ve iade kuralı | KALANLAR A1 | ADR adayı |
| Kargo entegrasyonu ve ücret sahipliği | KALANLAR A2 | ADR adayı |
| Satıcı doğrulama ve ilan ön onayı | KALANLAR A3 | ADR adayı |
| Ürün fotoğrafı depolama (yerel disk / nesne depolama) | KALANLAR B3 | ADR adayı |

---

## Yeni ADR nasıl yazılır?

1. [`template.md`](template.md) dosyasını kopyala: `NNN-kebab-case-baslik.md` (numara yukarıdaki "sıradaki numara"dan).
2. **Context**: kararın alındığı andaki durum, kısıtlar, tetikleyici. Gelecek zamanla değil, o anın gerçeğiyle yaz.
3. **Decision**: ne karar verildi; **değerlendirilen ve reddedilen alternatifler tablo halinde** ve her biri için ret gerekçesi. Alternatifsiz ADR eksik ADR'dir.
4. **Consequences**: olumlu, olumsuz ve nötr/trade-off sonuçlar. Olumsuz bölümü boş bırakılmaz; bedeli olmayan karar yoktur.
5. Bu dosyadaki tabloya satır ekle, "sıradaki numara"yı güncelle.
6. `docs/.tools/mkdocs.yml` nav bölümüne ekle.
7. İlgili `docs/features/` dosyasının *Tarihçe* bölümüne satır düş (`AGENTS.md` K1).

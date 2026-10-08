# Bahçeden: Monorepo Kuralları

> **Bu dosya monorepo'nun anayasasıdır ve tüm AI asistanları için tek doğruluk kaynağıdır (Single Source of Truth).**
> Bu depoda kod yazan, refactor eden veya doküman üreten yapay zeka asistanı (Claude Code, Cursor, GitHub Copilot, Antigravity vb.) aşağıdaki kurallara **istisnasız** uyar. Ayrı bir `CLAUDE.md`, `.cursorrules` veya `copilot-instructions.md` adaptörü **tutulmaz**; asistanlar bu dosyayı okur.
> Çelişki halinde bu dosya, onun da üstünde `docs/adr/` altındaki kabul edilmiş ADR'ler geçerlidir.

## 0. Bu Dosyanın Durumu (2026-10-06)

Bu anayasa **kod yazılmadan önce** yazıldı. Kural, doküman ve planlama apparatus'u **kuruldu**; uygulama kodu (`api/`, `web/`, `shared/contracts/`) **henüz yok.** Dolayısıyla:

- Doğrulanmış güncel durum tek yerdedir: [`docs/reference/00-current-state.md`](docs/reference/00-current-state.md). Bu dosyadaki "Hedef" işaretleri oradan teyit edilir.
- Aşağıda **"Hedef"** olarak işaretlenen yapı, komut ve betikler henüz kurulmadı; ilk dikey dilimde (K5) kurulur.
- Hiçbir kural "henüz kurulmadı" diye gevşemez: bir şey kurulmadıysa o işi yapan dilim onu kurmakla yükümlüdür.
- Asistan bu dosyada yazılı bir dosyaya atıf görüp dosyayı bulamazsa **uydurmaz**: ya o dilimin parçası olarak K1'e uygun şekilde oluşturur, ya da eksik olduğunu raporlar (§14).
- **Karar bekleyen konular ilgili alanda kod yazılmasını bloke eder.** Tam liste tek yerdedir: [`openspec/KALANLAR.md`](openspec/KALANLAR.md) (§15 bu listeye işaret eder, kopyasını tutmaz).

## 1. Proje Kimliği

- **Ürün:** Bahçeden, meyve-sebze üreticisini son tüketiciyle **doğrudan** buluşturan çok satıcılı pazaryeri. Manisa'daki bir üretici, bir hırdavatçının Hepsiburada'ya kaydolduğu kolaylıkla kaydolur, ürününü listeler ve **kargo ile** gönderir. Hal, komisyoncu ve aradaki fiyat katmanları zincirden çıkar.
- **Felsefe:** **Tek sözleşme, tek otorite.** `api/` ile `web/` arasındaki tüm arayüz `shared/contracts/openapi.yaml` dosyasıdır ([ADR-003](docs/adr/003-openapi-as-single-contract.md)); `web/` bu sözleşmeden **türetilen** tiplerle konuşur, sözleşmeyi tahmin etmez ve API tipi elle yazmaz. Sözleşme `api/` içindeki DTO ve anotasyonlardan **üretilir** ([ADR-009](docs/adr/009-contract-generated-from-spring.md)): tasarım kararı DTO'da alınır, dosya onu yansıtır ve tazeliği CI'da zorunludur. Veritabanı ve iş kuralları tek otorite olan `api/`'de yaşar.
- **Çok satıcılı ve kişisel veri taşıyan bir sistemdir.** Her sorgu satıcı/kullanıcı kapsamında yetkilendirilir (§11.6); kişisel veri (ad, adres, telefon, IBAN, kargo takip) KVKK kapsamındadır ve K10 ile korunur. Bu, tek tenant bir panelden **temel farktır**: "tüm kayıtları getir" tipi bir sorgu güvenlik açığıdır.
- **Durum:** Doğrulanmış güncel durum ve bilinen sınırlamalar **tek yerde** tutulur: [`docs/reference/00-current-state.md`](docs/reference/00-current-state.md). Sunum, araştırma notu ve yol haritası belgelerindeki ifadeler production gerçeği sayılmaz; şüphede kod ve o dosya esastır.
- **Dokümantasyon:** Diátaxis (`docs/`): `product/`, `research/`, `adr/`, `reference/`, `how-to/`, `tutorials/`, `explanation/`, `features/`.
- **Dil:** Dokümanlar, kod yorumları, Javadoc/JSDoc **Türkçe**. Kullanıcıya görünen metinler (arayüz, doğrulama ve hata mesajları, e-postalar) **Türkçe ve İngilizce**: kaynak ve varsayılan dil Türkçe (`tr`), ikinci dil İngilizce (`en`); metinler kodda değil mesaj dosyalarındadır ([ADR-029](docs/adr/029-next-intl-without-routing.md), [ADR-031](docs/adr/031-api-messages-by-accept-language.md)). Kod tanımlayıcıları (paket, sınıf, metot, değişken, bileşen, dosya adı, CSS sınıfı), API alanları, DB tablo/kolon adları, enum değerleri **İngilizce**; JSON alanları `camelCase`, DB `snake_case`, ortam değişkenleri `SCREAMING_SNAKE_CASE`. Commit mesajları **İngilizce** (§13).

## 2. Monorepo Haritası

| Yol | Sorumluluk | Durum |
|---|---|---|
| `api/` | Spring Boot 3 (Java 21, Maven) REST API: domain modeli, iş kuralları, persistence (JPA + Liquibase), kimlik doğrulama/yetkilendirme, sipariş & kargo akışı, bildirimler. **Tek yazma otoritesi.** | Hedef |
| `web/` | Next.js (App Router, TypeScript) — alıcı vitrini **ve** satıcı paneli. Yalnızca HTTP ile `api/`'ye konuşur. | Hedef |
| `shared/` | Sözleşme: `contracts/openapi.yaml` — `api/` DTO'larından **üretilir**, commit'lenir, elle düzenlenmez ([ADR-009](docs/adr/009-contract-generated-from-spring.md)). Üretilen TS tipleri (`shared/generated/`) ve `.env` türevleri **gitignore'dadır.** | Hedef |
| `docs/` | Diátaxis dokümantasyonu + [`adr/`](docs/adr/index.md) (mimari kararlar) + [`features/`](docs/features/index.md) (iş kayıtları: ne yapar, ne zaman ne değişti) + `.tools/` (MkDocs portalı). | ✅ Kurulu |
| `openspec/` | Spec-driven change **çalışma alanı**: `specs/` (geçerli davranış), `changes/` (açık planlar), `changes/archive/`, [`KALANLAR.md`](openspec/KALANLAR.md) (ekip kararı bekleyenler). **Kalıcı araçtır.** | ✅ Kurulu (`specs/` boş: henüz change yok) |
| `.claude/` | `/opsx:*` komutları (`commands/opsx/`) ve openspec skill'leri (`skills/`). Karar ve konvansiyonların tek kaynağı bu dosya ve `docs/adr/`'dir; branch notu tutulmaz. | ✅ Kurulu |
| `.githooks/pre-commit` | Değişen katmanın doğrulamasını commit öncesi koşar + K6/K10 taraması (`git config core.hooksPath .githooks`). | ✅ Kurulu |
| `.github/workflows/ci.yml` | CI: `api` testleri, `web` lint + typecheck + test + build, `docs` strict derleme, K6/K10 kural denetimi. | ✅ Kurulu, 4/4 yeşil |
| `docker-compose.yml` | Geliştirme ortamı: `db` (PostgreSQL 17), `api`, `web`, `mailpit` (e-posta yakalama). | Hedef |

**Yeni üst seviye dizin açmak mimari karardır** (ADR + kullanıcı onayı). "Ortak kod" için üçüncü bir modül (`common/`, `core-lib/`) açmak §3'ü ihlal eder; paylaşılan tek şey sözleşmedir.

## 3. Bağımlılık Yönü: Tek Yönlü, Yukarıdan Aşağıya

```text
docs/adr/  (neden)  →  openspec/specs/  (ne: davranış)
        │
        ▼
api/  (domain + PostgreSQL: tek doğruluk kaynağı)
        │  üretir ve doğrular: OpenAPI sözleşmesi
        ▼
shared/contracts/openapi.yaml  (sözleşme, commit'lenir)
        │  üretir: TypeScript tipleri (shared/generated/, gitignore)
        ▼
web/  (yalnızca HTTP ile api/'ye konuşur)
```

**Kesin kurallar:**

- `shared/` hiçbir modüle bağımlı olmaz; herkes `shared/`'a bağımlı olabilir.
- `api/` ile `web/` birbirini **import etmez** ve birbirinin dosya sistemine dokunmaz. Tek sözleşme: `openapi.yaml` + `docs/reference/` altında belgelenmiş akışlar.
- `web/` **veritabanına doğrudan erişmez**: DB sürücüsü, ORM, bağlantı dizesi, SQL `web/` içinde yasaktır. Sunucu tarafı render sırasında da veri yalnızca `api/` üzerinden alınır.
- `api/` istemciye özgü bilgi taşımaz: UI etiketi, rota adı, CSS sınıfı, ekran düzeni `api/` içinde yer almaz. (Kullanıcıya dönen **doğrulama ve hata mesajları** istisnadır; onlar Türkçedir ve sözleşmenin parçasıdır.)
- Dış servisler (kargo firması, ödeme sağlayıcı, SMS/e-posta) **yalnızca** `api/` üzerinden çağrılır; `web/` bir dış servise kendi anahtarıyla gitmez. (İstisna: sağlayıcının zorunlu kıldığı tarayıcı tarafı SDK'sı — ADR gerektirir.)
- Circular dependency **yasaktır.** Dependency yönünü değiştirecek her tasarım ADR gerektirir ve kullanıcı onayı olmadan uygulanmaz.
- **MVP ölçeği için over-engineering yapılmaz.** Hedef: az sayıda satıcı, tek bölge, tek sunucu. Bugün gerekmeyen soyutlama (mikroservis, event bus, kuyruk, CQRS, plugin sistemi, çok bölgeli kurulum, genel amaçlı framework, konfigürasyon üstüne konfigürasyon) eklenmez; "ileride lazım olur" gerekçe değildir, ihtiyaç doğduğunda ADR ile gelir.

## 4. Dokunulmaz Kurallar

### K1: Documentation-First (Önce Doküman)

Yeni bir özellik, davranış değişikliği veya mimari karar için **ilk adım ASLA kod veya sözleşme değildir.** İki yol vardır; ikisi de dokümanla başlar:

| Kapsam | Yol | Zorunlu çıktı |
|---|---|---|
| Spec'te yazılı davranışı değiştiren / ekran, endpoint, domain varlığı, DB tablosu ekleyen iş | OpenSpec change: `openspec/changes/<ad>/` (proposal → design → tasks → specs). Planlama artefaktıdır; kullanıcı onaylamadan uygulama başlamaz. Ayrıntı §5. | Aşağıdaki "kalıcı kayıt" satırının tamamı |
| Küçük, sınırlı değişiklik (bug fix, metin, stil, doküman) | Doğrudan `docs/` altındaki doğru şablon | Aynısı |

**Kalıcı kayıt her iki yolda da `docs/` altındadır; `openspec/` tek başına kayıt sayılmaz:**

| İhtiyaç | Yer | Biçim |
|---|---|---|
| Geri dönüşü maliyetli yapısal karar: `design.md`'deki **her `D<n>` maddesi dahil** | `docs/adr/NNN-kebab-case.md` + `docs/adr/index.md` tablosu | Nygard ADR: Başlık · Tarih · Durum (Proposed / Accepted / Deprecated / Superseded by ADR-XXX) · **Context** · **Decision** (değerlendirilen ve reddedilen alternatifler dahil) · **Consequences** (Olumlu / Olumsuz / Trade-off) |
| Kullanıcıya dokunan özellik / modül | `docs/product/*.md` | Kullanıcı problemi · değer önerisi · rakip analizi · kapsam ve kabul kriterleri · başarı metrikleri |
| Yeni kütüphane / servis / sağlayıcı denemesi | `docs/research/*.md` | Amaç · metodoloji ve alternatifler · bulgular (ölçüm) · sonuç ve öneri |
| Operatör/geliştirici görevi | `docs/how-to/*.md` + `docs/how-to/index.md` tablosu | Kısa giriş · ön koşullar · numaralı adımlar (gerçek komutlar) · olası hatalar · ADR referansları |
| Sistemin mevcut halinin kaydı (ER, akış, API, tasarım sistemi) | `docs/reference/00..NN-*.md` | - |
| **Yapılan işin kaydı: büyük küçük her iş, iş bitince** | `docs/features/<feature>.md` → *Tarihçe*'ye tek satır; feature'a bağlı değilse `docs/features/_genel.md` | `- YYYY-MM-DD: ne değişti (ADR-NNN / change adı / PR #n)` |

**Feature dosyası kuralı:** `docs/features/` içerik üretmez; ADR/spec/change/how-to'ya link verir ve değişimi kronolojik tutar. Dosya başlığında Durum (`Fikir` · `Geliştirmede` · `Yayında` · `Kaldırıldı`), ilk sürüm ve son değişiklik tarihi; gövdede *Ne yapar* (3–6 cümle, kullanıcı dili), *Bilinçli sınırlar*, *Açık konular*, *Tarihçe*. *Tarihçe* **append-only**'dir (yeni satır en üste; eski satır düzenlenmez, geri alma da yeni satırdır). Feature'lar **ürün seviyesinde** tanımlanır (örn. "Satıcı Kaydı ve Onayı", "Ürün İlanları", "Sipariş ve Ödeme", "Kargo ve Teslimat"); teknik capability'ler dosyanın içinde listelenir, ayrı dosya olmaz. OpenSpec'ten geçmeyen küçük bir iş bile satırını bırakır; bu, küçük işlerin iz bırakmaması boşluğunu kapatan tek mekanizmadır.

### K2: Sözleşme Senkronu (Monorepo Bilinci)

İstemciye görünen davranışı etkileyen her geliştirme **sözleşmeyi değiştirir** ve sözleşme `api/` içindeki DTO ile anotasyonlardan üretilir ([ADR-009](docs/adr/009-contract-generated-from-spring.md)). Akış: **DTO + `@Schema` → `openapi.yaml` (üretilir, commit'lenir) → `web` tipleri (üretilir, commit'lenmez) → ekran.**

Yeni/değişen her alanda zorunlu: tip, Türkçe `description` ve `example` (`@Schema` ile DTO'da verilir); uygunsa `enum`, Bean Validation kısıtları (`@Size`, `@Positive`, `@NotBlank` — bunlar spec'e yansır), zorunluluk. Para alanları minor unit tamsayı (kuruş) + `currency` ([ADR-006](docs/adr/006-money-as-minor-unit.md)); tarih/saat `date-time` (UTC, ISO-8601); sayfalama, sıralama ve hata gövdesi tüm uçlarda **aynı** şekli kullanır (§11.5).

**Sözleşme kalitesi kodun sorumluluğudur:** açıklamasız veya örneksiz bir alan, üretilmiş spec'i zayıflatır. Review bu noktaya bakar. `openapi.yaml` **elle düzenlenmez** (K8); çıktı yanlışsa DTO düzeltilir ve yeniden üretilir.

Kırıcı değişiklikte geriye dönük uyum düşünülür: alan silmek / yeniden adlandırmak / tipini değiştirmek **onay gerektirir** (§9); önce yeni alan eklenir, eski alan `deprecated` işaretlenir, istemci geçtikten sonra silinir.

`shared/` altında bir değişiklik yapıldığında **etki matrisi** doldurulur ve kullanıcıya sunulur:

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet / Hayır | controller, DTO, mapper, service, entity, Liquibase changeset, yetkilendirme kuralı, sözleşme doğrulama testi |
| `web/` | Evet / Hayır | üretilen tipler, veri çekme katmanı, form ve doğrulama, liste/detay ekranı, boş/hata durumu, metinler |
| DB | Evet / Hayır | tablo/kolon/indeks, changeset, `--rollback` planı, mevcut veri dönüşümü |
| `docs/` | **Her zaman** | `docs/reference/` API ve ER sayfaları; etkilenen `docs/features/` *Tarihçe* satırı |

### K3: Top-Down Bağımlılık

§3'teki kurallar. İstisna yok.

### K4: Docs-as-Code

Kaynak her değiştiğinde üretilmiş çıktılar **yeniden üretilir**, elle yazılmaz:

| Kaynak | Üretilen | Tazelik kontrolü |
|---|---|---|
| `api/` DTO + controller anotasyonları | `shared/contracts/openapi.yaml` (commit'lenir) | `api` testi: üretilen spec commit'lenenden farklıysa **kırmızı** ([ADR-009](docs/adr/009-contract-generated-from-spring.md)) |
| `shared/contracts/openapi.yaml` | `web` TypeScript tipleri (`shared/generated/`, commit'lenmez) | `npm run typecheck`; CI'da her koşuda yeniden üretilir |
| Liquibase changelog'ları | `docs/reference/` ER diyagramı | Elle güncellenir (changeset ile aynı commit'te) |

> [!NOTE]
> Üreteç ve tazelik testi **ilk dilimde kurulur**. Kurulana kadar `docs/reference/` içindeki API sayfaları aynı commit'te elle güncellenir; kurulduktan sonra üretilmiş dosyaları elle düzenlemek K8 ihlalidir. Dokümantasyon portalının strict derlemesi CI'da zorunludur (`.github/workflows/ci.yml` → `docs`).

### K5: Dikey Dilim (Vertical Slice)

İş, uçtan uca çalışan **tek bir akış** olarak kesilir: doküman → sözleşme → docs üretimi → changeset → entity/repository → service (iş kuralı) → controller → üretilen tipler → `web` ekranı → test → doğrulama. "Önce tüm entity'ler, sonra tüm controller'lar" gibi yatay yığınlar **yasaktır.** Bir openspec change'in `tasks.md`'si de bu mantıkla fazlanır: her faz tek başına çalışır durumda bırakılır.

### K6: Yarım İş Bırakma

Kodda `TODO`, `FIXME`, `XXX`, `HACK`, placeholder metin, hardcode mock/sahte veri, `System.out.println`, `console.log`, `debugger`, boş `catch`, yorum satırına alınmış ölü kod, `@Disabled` / `it.skip` ile susturulmuş test **bırakılmaz.** Geliştirme verisi gerekiyorsa idempotent seed (dev bağlamına bağlı Liquibase changeset'i) veya belgelenmiş bir geliştirme komutu kullanılır.

Bir dilimi bitiremiyorsan durur ve **açıkça raporlarsın**: ne bitti, ne bitmedi, hangi dosyada / adımda kaldın, neden, sıradaki adım ne. `tasks.md` kutuları yalnızca gerçekten biten iş için işaretlenir; dış/insan işi olan görev (kargo sözleşmesi, ödeme sağlayıcı başvurusu, domain satın alma) açık bırakılıp notlanır.

### K7: Bağımlılık (Dependency) Kararları

Bağımlılık bütçesi bir ADR'dir: [ADR-002](docs/adr/002-dependency-budget.md) (Accepted). Bütçe:

| Katman | İzinli | Not |
|---|---|---|
| `api/` runtime | `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `postgresql` sürücüsü, `liquibase-core`, `springdoc-openapi-starter-webmvc-api` (spec üretimi; UI paketi **değil**), `spring-boot-starter-oauth2-resource-server` (JWT imzalama/doğrulama; Nimbus JOSE dahil), `spring-boot-starter-mail` (SMTP; ADR-017) | Java 21, Maven, Spring Boot 3.x. Üçüncü parti JWT kütüphanesi (jjwt vb.) **alınmaz**: Spring'in kendi paketi yeterli |
| `api/` test | `spring-boot-starter-test` (JUnit 5 + AssertJ + MockMvc), `spring-security-test`, Testcontainers (PostgreSQL) | Test gerçek PostgreSQL'e karşı koşar; H2 ile sapma kabul edilmez |
| `web/` runtime | `next`, `react`, `react-dom`, `@base-ui/react`, `class-variance-authority`, `clsx`, `tailwind-merge`, `lucide-react`, `next-intl` | TypeScript, App Router. UI: shadcn/ui `base-nova` bileşenleri projeye kopyalanır, primitive Base UI, ikon Lucide ([ADR-027](docs/adr/027-shadcn-ui-design-system.md)); `cn` ve `shadcn` paketleri **alınmaz**. Çeviri next-intl, yönlendirmesiz; `createNextIntlPlugin` **kullanılmaz** (yerel ikili yükler), `next-intl/config` takma adı `next.config.ts`'te ([ADR-029](docs/adr/029-next-intl-without-routing.md)) |
| `web/` dev | `typescript`, `eslint` (+ `eslint-config-next`), `vitest` + `@testing-library/react`, `@playwright/test`, OpenAPI → TS tip üreteci, `tailwindcss` + `@tailwindcss/postcss`, `tw-animate-css` | Tailwind v4 ([ADR-027](docs/adr/027-shadcn-ui-design-system.md)) |
| Sistem | PostgreSQL 17, Docker Compose, `mailpit` (geliştirme e-postası) | |

Bu listenin **dışındaki** her paket / servis / Docker imajı **mimari karardır.** Eklemeden önce kullanıcıya yaz ve **onay al**:

1. Spring / Java stdlib / Next.js / tarayıcı API'leri bunu **neden** karşılayamıyor?
2. En az iki alternatif ve neden reddedildikleri.
3. Yük: boyut, transitive bağımlılıklar, lisans, bakım durumu (son sürüm tarihi), güvenlik yüzeyi, imaj boyutu, derleme süresi.
4. Hangi katmana ekleniyor; dependency yönünü bozuyor mu?

Onay gelirse ADR yazılır ve paket **pin'li** sürümle eklenir (Maven'da açık `<version>` veya BOM; npm'de tam sürüm, `^` değil). Onaysız `pom.xml` / `package.json` düzenlemesi, `npm install`, `<script src=…>` ve CDN kullanımı **yasaktır.** UI kütüphanesi, ikon seti, CSS framework'ü, state yönetimi kütüphanesi, ORM üstü soyutlama bu kuralın en sık ihlal edilen kalemleridir: önce ADR.

### K8: Otomatik Üretilmiş Dosyalara Dokunma

Elle düzenlenmez: `shared/generated/*` · `docs/reference/` içindeki üretilmiş bloklar · `web/.next/`, `web/node_modules/` · `api/target/` · paket yöneticisi lock dosyalarının içeriği (sürüm değişikliği yalnızca paket yöneticisi komutuyla, K7 onayından sonra) · **uygulanmış** Liquibase changeset'leri (yeni changeset yazılır, eskisi düzenlenmez). Çıktı yanlışsa kaynağı (sözleşme, changeset, betik) düzelt ve yeniden üret.

### K9: Önce Mevcut Kod, Sonra Minimum Değişiklik

- **Existing-code-first.** Yeni bir service, utility, validator, mapper, hook, bileşen, sabit katalogu veya test yardımcısı yazmadan önce aynı sorumluluğu taşıyan mevcut kod aranır: `grep` ile ad/kavram araması, §11–§12'deki yerleşim tabloları, ilgili spec ve `docs/reference/` sayfası. Bulunan kod **genişletilir**; yanına ikinci bir kopya yazılmaz. Bilinçli olarak yeni bir şey yazılıyorsa raporda "mevcut X neden yetmedi" tek cümleyle söylenir.
- **Minimum necessary change.** Akış: *istenen değişiklik → etkilenen bileşenler → en küçük güvenli değişiklik.* Task'ın kapsamı dışındaki dosyaya dokunulmaz; yol üstündeki kod "daha iyi" olsun diye yeniden yazılmaz, yeniden adlandırılmaz, biçimlendirilmez (formatter yalnızca dokunulan dosyada çalışır). Task dışı bir değişiklik gerçekten gerekiyorsa (bloklayan hata, K6 ihlali, ADR'ye aykırılık, güvenlik açığı) **ayrı ve gerekçeli** raporlanır (§14); gerekçesiz task dışı diff review'da reddedilir. Toplu refactor kendi başına bir change konusudur (§5), bir feature'ın yan ürünü değil.

### K10: Secret ve Hassas Veri AI Bağlamına Girmez

AI asistanının bağlamına (prompt, eklenen dosya, terminal çıktısı, hata mesajı, ekran görüntüsü) **girmez**: API anahtarı, token, parola, JWT imza anahtarı, DB bağlantı dizesi, ödeme sağlayıcı (sanal POS) kimlik bilgileri, kargo firması API anahtarı, SMTP/SMS kimliği, üretim veya gerçek müşteri verisi (ad, adres, telefon, e-posta, TCKN, IBAN, kart verisi, kargo takip numarası, sipariş dökümü), üretim DB dökümleri.

- `.env` dosyaları (`/.env`, `api/.env`, `web/.env*`) gitignore'dadır ve asistana **okutulmaz**; örnek gerekiyorsa `api/.env.example` / `web/.env.example` ve placeholder değerler (`degistirin-bu-degeri`, `postgres://user:pass@db:5432/bahceden`) kullanılır. Değişken **adı** paylaşılabilir, **değeri** paylaşılmaz.
- Kodda secret, host, parola, API key **yoktur**; yalnızca ortam değişkeninden okunur (`api/`: `@ConfigurationProperties` ile tip güvenli bağlama; `web/`: sunucu tarafı `process.env`, tarayıcıya **yalnızca** `NEXT_PUBLIC_` ile açıkça işaretlenmiş ve gizli olmayan değerler gider). Üretimde eksik veya varsayılan secret ile uygulama **başlamaz** (açılışta fail-fast doğrulama).
- Kart verisi sistemde **saklanmaz ve loglanmaz**; ödeme sağlayıcısının tokenizasyonu kullanılır. Kişisel veri log'a maskeli yazılır (e-posta `a***@example.com`, telefon son 4 hane, IBAN son 4 hane); tam adres, sipariş içeriği ve takip numarası uygulama log'una yazılmaz.
- Log, test, fixture ve seed verisi **sahte** olur (`ornek.test` alan adı, `+90 555 000 00 00`, `Ornek Mahallesi`). Gerçek gönderim yapan (SMS / e-posta / ödeme / kargo) veya gerçek kimlik bilgisi taşıyan test **yasaktır**; geliştirmede e-posta `mailpit`'e, diğer sağlayıcılar `fake` / sandbox moduna gider.
- Bulut tabanlı asistanlar dosya içeriğini sağlayıcıya gönderir; bu yüzden kural yalnızca "commit etme" değil, **"bağlama alma"** kuralıdır. Asistan bir dosyada gerçek bir secret görürse onu kopyalamaz, loglamaz, raporda tekrar etmez; kullanıcıya dosya adı ve satırıyla bildirir ve rotasyon önerir.

## 5. OpenSpec ile Çalışma (Spec-Driven Change'ler)

`openspec/` kalıcı bir araçtır. Sistemin **geçerli davranış sözleşmesi** `openspec/specs/<capability>/spec.md` dosyalarıdır (`### Requirement` + `#### Scenario`, WHEN/THEN, SHALL dili); `docs/adr/` ise bu davranışın **neden** böyle olduğunu kaydeder.

### 5.1 Ne zaman change açılır

- Bir spec'te yazılı davranışı değiştiren, yeni ekran / endpoint / domain varlığı / DB tablosu ekleyen veya birden fazla katmana dokunan her iş → **change** (ad kebab-case ve fiille başlar: `add-…`, `change-…`, `remove-…`).
- Spec'i değiştirmeyen küçük işler (bug fix, metin, stil, doküman, kapsamı dar refactor) change gerektirmez. Ancak bu sırada spec ile kod arasında fark görülürse bu bir change konusudur; kod sessizce değiştirilmez.
- Belirsizlik varsa önce keşif / konuşma; karar verilmeden artefakt üretilmez.

### 5.2 Artefaktlar ve bağlayıcı kuralları

| Artefakt | İçerik | Kural |
|---|---|---|
| `proposal.md` | Why · What Changes · Capabilities · Impact | Kapsam ve **non-goals** açık; `docs/product/` ile çelişmez |
| `design.md` | Kararlar `D1, D2, …`: her biri Gerekçe + reddedilen Alternatif; Risks / Trade-offs; Migration Plan; Open Questions | Her `D<n>` archive'dan önce `docs/adr/` altına ADR olur (K1). Kabul edilmiş bir ADR'ye aykırı karar yazılamaz; önce ADR *superseded* edilir |
| `tasks.md` | Fazlı görev listesi (`- [ ] n.m …`) | K5: her faz tek başına çalışır durumda bırakılır; kutu yalnızca gerçekten biten iş için işaretlenir; dış/insan işi açık kalır ve notlanır |
| `specs/<capability>/spec.md` | Delta spec: `## ADDED / MODIFIED / REMOVED Requirements` | Mevcut capability yolu korunur; yeni capability adı mevcut düzeni izler; sync ile ana spec'e birleşir |

### 5.3 Yaşam döngüsü ve sınırlar

1. **Propose** yalnızca planlamadır: kod yazılmaz; kullanıcı artefaktları onaylamadan uygulama başlamaz.
2. **Apply** `tasks.md`'yi sırayla uygular. Uygulama sırasında tasarım değişirse önce artefaktlar güncellenir; artefakt ile kod ayrışamaz.
3. Her change `docs/reference/`'ı günceller, gerekiyorsa `docs/how-to/` sayfası ekler, ilgili `docs/features/` dosyasının *Tarihçe*'sine satır düşer (yeni feature ise dosya açılır); sözleşmeye dokunduysa K2 ve K4 uygulanır.
4. **Sync** delta spec'leri ana spec'lere birleştirir; spec doğrulaması temiz olmalıdır.
5. **Archive** ön koşulları; tamamı sağlanmadan archive **yapılmaz**, archive kullanıcı onayı ister (§9): tasks tamam ya da açık görevler notlu · sync yapıldı · `design.md`'deki her karar ADR'de · `docs/features/` dosyası ve *Tarihçe* güncel · docs güncel · §7 doğrulama komutları temiz. Hedef: `openspec/changes/archive/YYYY-MM-DD-<ad>/`.
6. Ekip / operasyon / ticari karar bekleyen konular `openspec/KALANLAR.md`'ye yazılır; karara bağlanınca ADR olur ve listeden düşer.

### 5.4 Çelişkide öncelik sırası (source of truth)

İki ayrı soru vardır; her birinin kendi sırası vardır ve ikisi karıştırılmaz:

| Soru | Sıra (üstteki alttakini ezer) |
|---|---|
| **"Sistem şu an ne yapıyor?"** (mevcut durum) | 1. Gerçek kod ve doğrulanmış runtime davranışı (test çıktısı, çalışan ortam) → 2. Kabul edilmiş ADR → 3. Güncel `docs/reference/` → 4. `docs/reference/00-current-state.md` özeti → 5. Eski sunum, araştırma, yol haritası |
| **"Sistem ne yapmalı?"** (hedef davranış) | 1. Kabul edilmiş ADR (neden) → 2. `openspec/specs/` (ne) → 3. `docs/reference/` → 4. Kod |

Doküman (3–5) kod veya ADR'ye aykırıysa **doküman güncellenir**. Kod spec'ten sapıyorsa spec değil **kod** düzeltilir; spec bir ADR'ye aykırıysa önce ADR (*superseded*), sonra spec; ikisi de change ile. Kod ile ADR birbirine aykırıysa bu bir **sapma**dır: asistan hiçbirini sessizce değiştirmez, önce raporlar. Kararı olmayan sapma **UNDECIDED** olarak izlenir ve `00-current-state.md` "bilinen sapmalar" tablosuna yazılır; agent geçmişte yazılmış kodu kendiliğinden kabul edilmiş mimari karar saymaz.

## 6. Standart Geliştirme Akışı (AI destekli)

Bu akış hangi asistan kullanılırsa kullanılsın aynıdır. AI hiçbir adımda depodaki gerçek kodun ve insan review'unun yerine geçmez.

### 6.1 Bağlam toplama sırası

Bir task'a başlarken bağlam **bu sırayla** toplanır; sonraki adım öncekine dayanır ve hiçbiri atlanmaz:

1. **Task ve kabul kriterleri** (issue / user story; belirsizse soru sor).
2. **Proje kuralları:** bu dosya; ilgili `docs/how-to/`.
3. **Güncel durum:** `docs/reference/00-current-state.md` (bilinen sınırlamalar ve sapmalar dahil); `openspec/KALANLAR.md`'de bekleyen karara bağlıysa önce onu gündeme getir.
4. **İlgili ADR'ler:** `docs/adr/index.md`'den konuya dokunan **yürürlükteki** kararlar (superseded olanlar değil).
5. **Referans ve spec:** `docs/reference/`, `openspec/specs/<capability>/spec.md`, sözleşme (`shared/contracts/openapi.yaml`).
6. **Mevcut kaynak kod:** aynı sorumluluğu taşıyan modüller (K9), çağıranlar ve çağrılanlar.
7. **Mevcut testler:** ilgili test sınıfları/dosyaları, `critical` etiketli testler; hangi davranış sabitlenmiş?

### 6.2 Adımlar

1. **Task'ı oku, sınıflandır** (§5.1: change mi, küçük iş mi) ve bağlamı §6.1 sırasıyla topla.
2. **Etkiyi belirle.** Etkilenen bileşenler; test etkisi (§6.3); doküman etkisi (§6.4); `shared/` değişiyorsa K2 etki matrisi; bağımlılık yönü (K3); yeni paket gerekiyor mu (K7); kişisel veri / yetkilendirme etkisi var mı (K10, §11.6).
3. **Küçük plan yaz ve onay al (K1).** OpenSpec change ya da doğrudan doküman; kabul kriterleri; **en küçük güvenli değişiklik** (K9). Kullanıcı onayı gelmeden kod yazılmaz.
4. **Sözleşme (K2)** ve **docs üretimi (K4)**, gerekiyorsa.
5. **Dilimi uygula (K5).** `shared` → `api` (changeset → entity → repository → service → controller → DTO) → üretilen tipler → `web` (veri çekme → ekran → durumlar); her adımda test.
6. **Testleri koştur.** §7 komutlarının tamamı; çıktıyı olduğu gibi raporla; kırmızı test "sonra" diye geçilmez. Başlangıçta başarısız testler ayrı kaydedilir; beklentiler sırf yeşil sonuç için gevşetilmez veya silinmez.
7. **Diff'i gözden geçir.** `git diff --stat` ile dokunulan her dosya task'la eşleştirilir; task dışı değişiklik ya geri alınır ya gerekçelendirilir (K9); K6 (TODO, debug, mock), K8 (üretilmiş dosya), K10 (secret, kişisel veri) taraması yapılır; `.gitignore`'a takılması gereken dosya (`.env`, `target/`, `node_modules/`, `.next/`, geçici betik, ekran görüntüsü) diff'te yoktur.
8. **Doküman kontrolü.** §6.4 listesi; ADR'ler, how-to, `docs/how-to/index.md`, **`docs/features/` Tarihçe satırı** (her iş; feature'sız iş `_genel.md`); güncel durum değiştiyse `00-current-state.md`. Change bittiyse sync + archive: ön koşullar §5.3; ADR'ler yazılmadan archive **yapılmaz.**
9. **Raporla (K6)** §14 formatında ve **insan review'una sun.** AI çıktısı review'suz `main`'e girmez; PR şablonu §13.

### 6.3 Test etkisi kontrolü

Her davranış değişikliğinde plan aşamasında cevaplanır ve raporda yazılır:

- Hangi mevcut testler etkilenir (kırılır, güncellenir)? Hangi `critical` test bu davranışı sabitliyor?
- Yeni test gerekli mi; hangi katmanda (domain/service birim testi, repository + Testcontainers, `MockMvc` sözleşme testi, `web` bileşen testi, Playwright akış testi)? **Yeni iş kuralı = `critical` etiketli test** (para, stok, sipariş durumu, yetkilendirme **her zaman** bu kapsamdadır).
- Regression riski nedir: hangi çağıranlar, hangi ekranlar, hangi sözleşme alanı, hangi yetkilendirme yolu?
- Test edilemeyen bir davranış mı ekleniyor (ödeme sağlayıcı, kargo API'si, zamanlanmış iş, e-posta)? Öyleyse nasıl izole edilir (sandbox / fake sağlayıcı, sabit saat kaynağı, mailpit); izole edilemiyorsa bu raporda **açık risk** olarak yazılır.

### 6.4 Doküman etkisi kontrolü

Her değişiklikte en az şunlar sorulur; "etkilenmiyor" cevabı da raporda yazılır:

| Kaynak | Ne zaman etkilenir |
|---|---|
| `docs/reference/` | Domain modeli, akış, endpoint, DTO, DB şeması, tasarım sistemi değişti |
| `docs/reference/00-current-state.md` | Bir katmanın durumu, bilinen bir sınırlama veya sapma değişti |
| `docs/adr/` | Geri dönüşü maliyetli karar verildi ya da mevcut ADR'ye aykırı davranıldı |
| `docs/how-to/` + `index.md` | Operatör / geliştirici görevi eklendi veya değişti |
| `docs/features/` Tarihçe | **Her iş** |
| `README.md` | Kurulum, komut, dizin yapısı değişti |
| Bu dosya (`AGENTS.md`) | Konvansiyon, yerleşim, komut, yasak, bağımlılık bütçesi değişti |

## 7. Komutlar (Hedef)

> Komut adları ilk dilimde `docker-compose.yml`, `pom.xml` ve `web/package.json` ile sabitlenir. Bir komut yoksa **uydurulmaz**; o dilim onu tanımlar ve bu bölüm güncellenir.

```bash
# Ortam
docker compose up                       # api http://localhost:8080, web http://localhost:3000, mailpit http://localhost:8025

# api/ doğrulama (her dilim sonunda, tamamı)
docker compose exec -T api ./mvnw -B verify             # derleme + birim/entegrasyon testleri
docker compose exec -T api ./mvnw -B test -Dgroups=critical
# Şema doğrulaması ayrı bir komut değildir: Liquibase changelog checksum'larını
# ve Hibernate şemayı uygulama AÇILIŞINDA doğrular (ADR-016).

# web/ doğrulama (her dilim sonunda, tamamı)
npm --prefix web ci
npm --prefix web run lint
npm --prefix web run typecheck          # next typegen + tsc --noEmit; sözleşme tipleri ve typedRoutes uyumlu mu
npm --prefix web test                   # Vitest
npm --prefix web run build              # Next.js production derlemesi
npm --prefix web run test:e2e           # Playwright (ortam ayakta olmalı)

# Sözleşme
npm --prefix web run gen:api            # openapi.yaml → shared/generated (K8: çıktı elle düzenlenmez)
```

Bugün çalışan komutlar (apparatus kurulu, kod yok):

```bash
# Dokümantasyon (her doküman değişikliğinde strict derleme)
./docs/.tools/serve.sh                  # portal http://localhost:8888
./docs/.tools/serve.sh --build          # strict derleme; CI'daki docs job ile aynı kontrol

# Git hook'u (klon başına bir kez)
git config core.hooksPath .githooks

# OpenSpec (CLI; /opsx:* komutları bunları çağırır — CLI henüz kurulu değil)
openspec list --json                    # açık change'ler
openspec status --change "<ad>" --json  # artefakt / görev durumu
openspec validate --specs               # ana spec'ler geçerli mi
```

## 8. Definition of Done

- [ ] Doküman yazıldı; ADR'ler `docs/adr/` + `index.md`; gerekiyorsa how-to + `how-to/index.md` (K1).
- [ ] `docs/features/` ilgili dosyada *Tarihçe* satırı eklendi; `features/index.md` tablosunda durum / son değişiklik güncel (feature'sız iş: `_genel.md`) (K1).
- [ ] Sözleşme güncellendi, etki matrisi yazıldı, üretilen tipler yeniden üretildi (K2, K4).
- [ ] Dependency yönü korunuyor; `web` DB'ye veya dış servise doğrudan gitmiyor (K3).
- [ ] Dilim uçtan uca çalışıyor; testi var; §7'deki tüm komutlar temiz (K5).
- [ ] TODO / placeholder / mock / debug çıktısı / susturulmuş test yok; `tasks.md` dürüst (K6).
- [ ] Yeni bağımlılık yoksa "yok" yazıldı; varsa onay + ADR + pin'li sürüm (K7).
- [ ] Üretilmiş dosyalara ve uygulanmış changeset'lere dokunulmadı (K8).
- [ ] Mevcut kod arandı ve genişletildi; diff task kapsamında, task dışı değişiklik gerekçeli (K9).
- [ ] Diff'te ve raporda secret / gerçek kişisel veri yok; `.env`, dökümler, geçici betik commit'te değil; log'lar maskeli (K10).
- [ ] Yetkilendirme: yeni/değişen her endpoint ve sorgu için sahiplik/rol kontrolü var ve testle sabitlendi (§11.6).
- [ ] Test etkisi (§6.3) ve doküman etkisi (§6.4) raporda cevaplandı; `00-current-state.md` gerekiyorsa güncel.
- [ ] Rapor §14 formatında; komut çıktıları gerçek; insan review'u bekleniyor.

## 9. Onay Gerektiren İşlemler

Kullanıcıya sormadan yapma:

- Yeni kütüphane / paket / servis / Docker imajı; sürüm yükseltmesi (Spring Boot, Next.js, Java, Node ana sürümü) (K7).
- Sözleşmede kırıcı değişiklik: alan silme, yeniden adlandırma, tip değiştirme, `required` değişikliği, endpoint kaldırma.
- Veri kaybı riski taşıyan şema değişikliği: kolon/tablo silme, tip daraltma, `NOT NULL` ekleme, geri alınamaz veri dönüşümü. (Uygulanmış changeset **düzenlenmez**; yeni changeset yazılır.)
- Dependency yönünü etkileyen tasarım; ADR'ye aykırı uygulama (önce ADR'yi *superseded* etmeyi öner).
- Kimlik doğrulama / yetkilendirme modelinde, ödeme veya kargo akışında değişiklik; fiyat, komisyon, iade kuralı gibi ticari mantık.
- `.github/workflows/` ve `docker-compose.yml` servis yapısı değişikliği.
- `git push`, `--force`, branch silme, history rewrite, `main`'e doğrudan commit. Commit'i yalnızca kullanıcı isterse at.
- Dosya/dizin silme, toplu yeniden adlandırma.
- Kişisel veri işleyen yeni bir alan/akış eklemek (KVKK etkisi) veya dış servise kişisel veri göndermek.
- OpenSpec change archive'lama.

## 10. `shared/` Kuralları (Sözleşme)

`contracts/openapi.yaml` **üretilmiş ama commit'lenen** bir dosyadır ([ADR-009](docs/adr/009-contract-generated-from-spring.md)): kaynağı `api/` içindeki DTO ve anotasyonlardır, depoda tutulma sebebi sözleşme değişikliğinin PR diff'inde görünmesidir.

- **Elle düzenlenmez** (K8). Çıktı yanlışsa DTO / anotasyon düzeltilir ve dosya yeniden üretilir. Tazeliği `api` testiyle zorunludur: üretilen spec commit'lenenden farklıysa test kırmızıdır.
- Her şema alanında Türkçe `description` ve `example` **zorunludur**; bunlar `@Schema` anotasyonuyla DTO'da verilir. `enum` değerleri İngilizce `SCREAMING_SNAKE_CASE` sabitlerdir ve `api` enum'larından üretildiği için zaten birebir aynıdır.
- **Sözleşmeye katman detayı sızdırılmaz:** şema adları domain kavramını anlatır, Java sarmalayıcı sınıf adını değil; tablo adı, dosya yolu, React bileşeni veya CSS sınıfı sözleşmede geçmez. Üretilen ad anlamsızsa `@Schema(name = ...)` ile düzeltilir.
- Sözleşmenin kopyası başka dizinde tutulmaz; `web` tiplerini `shared/generated/` altına üretir ve bu dizin **gitignore'dadır** (CI'da üretilir).
- Sürümleme: kırıcı değişiklik `v2` yolu veya `deprecated` geçişi gerektirir (§9); kırıcı değişiklik her zaman kullanıcı onayına tabidir ve diff'te görünür olmak zorundadır.

## 11. `api/` Kuralları (Spring Boot)

### 11.1 Rol ve sınırlar

`api/`, sistemin **tek yazma otoritesidir**: doğrulama, iş kuralı, yetkilendirme, para hesabı, durum geçişi burada yaşar. **Yasak:** UI düzeni/metni üretmek (doğrulama mesajları hariç), `web/` dosyalarını import etmek, istemciden gelen bir değere güvenerek yetkilendirmeyi atlamak, sunucuda hesaplanması gereken bir değeri (fiyat, toplam, komisyon, kargo ücreti, stok) istemciden almak.

### 11.2 Yerleşim ve katmanlar

- **Paket düzeni domain'e göredir, teknik katmana göre değil:** `com.bahceden.<domain>` (`seller`, `catalog`, `order`, `shipping`, `payment`, `account`, `notification`) + çapraz kesen `com.bahceden.common` (hata gövdesi, sayfalama, saat kaynağı, para tipi). "Tüm controller'lar bir pakette" düzeni **yasaktır.**
- **Domain paketi içi:** `*Controller` (ince: DTO → service → DTO) · `*Service` (iş kuralı; tek giriş noktası, `@Transactional` burada) · `*Repository` (Spring Data arayüzü; özel sorgular `@Query` veya Specification) · `domain/` (entity + değer nesnesi + enum) · `dto/` (request/response `record`'ları) · `*Mapper` (entity ↔ DTO; elle yazılır).
- Controller'da iş mantığı, service'te `HttpServletRequest`, entity'nin API'ye sızması **yasaktır**: dışarıya yalnızca DTO döner.
- Her sınıfın başında amacı ve dayandığı kararı söyleyen Türkçe Javadoc; yorumlar "ne"yi değil "neden"i anlatır ve ADR'ye referans verir (`(ADR-004)`).

### 11.3 Java kuralları

- Java 21: `record` (DTO ve değer nesneleri), sealed hiyerarşiler (durum/sonuç tipleri), `switch` pattern matching, `Optional` (alan olarak değil, dönüş tipinde).
- Constructor injection (`final` alanlar); `@Autowired` ile alan/field injection **yasaktır**.
- `null` yerine `Optional` / boş koleksiyon; koleksiyonlar dışa dönerken değiştirilemez (`List.copyOf`).
- Para: minor unit `long` (kuruş) + açık `Currency` ([ADR-006](docs/adr/006-money-as-minor-unit.md)); yuvarlama tek bir yardımcıda ve `critical` testle sabit. `double` ile para hesabı **yasaktır**.
- Zaman: UTC `Instant` ile saklanır; "şimdi" doğrudan `Instant.now()` ile değil, enjekte edilen `Clock` ile alınır (test edilebilirlik). Kullanıcıya gösterim zaman dilimi `web`'in işidir.
- Ayarlar yalnızca `@ConfigurationProperties` ile tip güvenli okunur; dağınık `@Value` ve kodda sabit host/secret **yasaktır** (K10). Her yeni değişken `api/.env.example`'a açıklamasıyla eklenir ve açılışta doğrulanır (`@Validated`).
- Logger `org.slf4j.Logger`; `System.out.println` **yasak**. Kişisel veri maskeli loglanır (K10). Beklenen iş hatası `WARN`/`INFO`, beklenmeyen `ERROR` + stack trace.
- Hata yönetimi: boş `catch` **yasak**; istisna yutulmaz. İş kuralı ihlali kendi istisna tipiyle fırlatılır ve tek bir `@RestControllerAdvice` ile §11.5 hata gövdesine çevrilir. Beklenen durumlar `RuntimeException` yakalanarak değil, açıkça modellenir.
- Sabit listeler ve varsayılanlar kodda iki kez yazılmaz: enum veya tek bir sabit sınıfı. Sihirli sayı (`10`, `0.18`, `3`) **yasaktır**; adlandırılmış sabit veya yapılandırma olur.

### 11.4 Persistence ve şema değişiklikleri

- Şema **yalnızca** Liquibase ile değişir ([ADR-016](docs/adr/016-liquibase-for-schema-migrations.md)): changelog'lar `api/src/main/resources/db/changelog/` altında **formatted SQL** (`NNN-kebab-case.sql`), master changelog yalnızca `include` listesi tutar. `ddl-auto` her ortamda `validate`; `update` / `create-drop` **yasaktır**.
- Her changeset `--rollback` satırları taşır ve kimliği dosya adıyla aynıdır (`--changeset bahceden:NNN-kebab-case`).
- **Bağlam her ortamda açıkça bildirilir** (`spring.liquibase.contexts`): Liquibase, bağlam verilmezse bağlam etiketli changeset'leri de uygular, yani `contextFilter` tek başına koruma sağlamaz. Geliştirme verisi `contextFilter:dev` ile işaretlenir ve bu izolasyon testle sabitlenir.
- Uygulanmış changeset düzenlenmez veya yeniden adlandırılmaz (K8); düzeltme yeni bir changeset'tir. Liquibase checksum doğrular: değiştirilen changeset uygulamayı açılışta düşürür. Veri changeset'leri idempotenttir.
- Entity: `@Id` `BIGSERIAL`; iş anahtarları (sipariş numarası gibi) ayrı ve indeksli; her tabloda `created_at` / `updated_at`. Tablo ve kolon adları `snake_case`, tablo adı çoğul.
- Silme davranışı **bilinçli seçilir ve ADR'ye bağlanır**: sipariş ve gönderim kayıtları **silinmez** (yasal kayıt; iptal/iade bir durum geçişidir); satıcı veya ürün kaldırıldığında geçmiş siparişler korunur (soft delete / arşiv alanı). `CascadeType.REMOVE` ve `orphanRemoval` yalnızca gerçek sahiplik ilişkisinde.
- N+1 sorgu **kabul edilmez**: liste uçları için `@EntityGraph` / `join fetch`; iddialar sorgu sayısı testiyle sabitlenir. Sayfalama her liste ucunda zorunludur, üst sınır sunucuda sabittir.
- Ham SQL yalnızca changeset'lerde; uygulama kodunda ORM / Specification kullanılır. Kullanıcı girdisi asla sorgu metnine birleştirilmez.
- Para, stok ve sipariş durumu değiştiren akışlar tek transaction içinde ve yarış koşuluna karşı korumalıdır (iyimser kilit `@Version` veya `SELECT … FOR UPDATE`); bu davranış `critical` testle sabitlenir.

### 11.5 HTTP sözleşmesi

- REST: çoğul kaynak adları (`/api/v1/products`, `/api/v1/orders/{id}/shipments`), fiil yok; durum değiştiren eylemler alt kaynak veya açık komut ucu (`POST /orders/{id}/cancel`).
- Doğrulama Bean Validation ile DTO üzerinde (`@NotBlank`, `@Size`, `@Positive`); mesajlar tek mesaj kaynağından: `message = "{validation.…}"` anahtarı, metni `messages.properties` (Türkçe) ve `messages_en.properties` içinde ([ADR-031](docs/adr/031-api-messages-by-accept-language.md)). Kodda kullanıcı metni yazılmaz; `ApiException` anahtar taşır. Yeni anahtar iki dosyaya birlikte eklenir (`MessagesCatalogTest`).
- **Tek hata gövdesi** (tüm uçlar, `@RestControllerAdvice`): `{"code": "ORDER_ALREADY_CANCELLED", "message": "…", "fieldErrors": [{"field": "quantity", "message": "…"}]}`; `code` İngilizce sabit ve dilden bağımsız, `message` kullanıcıya gösterilebilir ve isteğin dilinde (`Accept-Language`; varsayılan Türkçe). E-postalar hesabın dilindedir ([ADR-032](docs/adr/032-account-locale-stored-and-refreshed-on-login.md)). Stack trace, SQL ve iç sınıf adı gövdeye **sızmaz**.
- Durum kodları: 200 / 201 (+`Location`) / 204 · 400 doğrulama · 401 kimlik yok · 403 yetki yok · 404 yok **veya başkasına ait** (sahiplik ihlali varlığı sızdırmaz) · 409 çakışma / durum ihlali · 422 iş kuralı · 429 hız sınırı.
- Liste yanıtı tek şekil: `{"items": [...], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0}`. Sıralama ve filtre parametreleri beyaz listeyle doğrulanır.
- Para ve miktar yanıtlarda `{"amount": 12550, "currency": "TRY"}` (minor unit); istemci hesaplamaz, gösterir.
- Yazma uçları idempotency gerektiren yerlerde (ödeme başlatma, sipariş oluşturma) `Idempotency-Key` başlığını destekler.
- Sözleşme ile kod ayrışamaz: her endpoint için sözleşmeye karşı bir `MockMvc` testi vardır (alan adları, zorunluluklar, hata gövdesi).

### 11.6 Güvenlik ve yetkilendirme (çok satıcılı sistem)

- Roller: `ROLE_CUSTOMER`, `ROLE_SELLER`, `ROLE_ADMIN`. Varsayılan **deny**: her endpoint açıkça yetkilendirilir; herkese açık uçlar (vitrin, ürün detayı, kayıt, giriş) açıkça işaretlenir.
- **Sahiplik kontrolü zorunludur ve sorgu seviyesindedir** ([ADR-007](docs/adr/007-query-level-ownership-scoping.md)): satıcı yalnızca kendi ürün / sipariş / gönderimini görür ve değiştirir. Bu kontrol `findById` sonrası `if` ile değil, **repository sorgusunda** (`findByIdAndSellerId`) yapılır; her sahiplik kuralının `critical` testi vardır (başkasının kaydına erişim → 404).
- **Kimlik doğrulama ([ADR-008](docs/adr/008-jwt-auth-with-opaque-refresh.md)):** kısa ömürlü (15 dk) JWT erişim token'ı + opak, hash'li saklanan, rotasyonlu ve iptal edilebilir yenileme token'ı. JWT imzalama/doğrulama `spring-boot-starter-oauth2-resource-server` ile; imza anahtarı ortamdan okunur. `api` tarafında **tek** kimlik yolu vardır: `web` çerezleri kendi taşıma detayıdır, `api` yalnızca `Authorization: Bearer` görür.
- **Hesap modeli ([ADR-008](docs/adr/008-jwt-auth-with-opaque-refresh.md)):** tek `accounts` tablosu + `account_roles`; aynı kişi hem alıcı hem satıcı olabilir. Parola BCrypt ile hash'lenir. Doğrulama ve sıfırlama token'ları tek kullanımlık, süreli ve hash'li saklanır; yanıtlar e-postanın kayıtlı olup olmadığını açığa çıkarmaz.
- **E-posta doğrulaması girişi engellemez ([ADR-017](docs/adr/017-email-verification-non-blocking-and-2fa-switch.md)):** hesap doğrudan `ACTIVE` açılır, doğrulama bağlantısı gönderilir ve tüketildiğinde `email_verified` işaretlenir. E-postanın kimlik akışına girmesi hesap bazlı `two_factor_enabled` anahtarına bağlıdır; anahtar varsayılan **kapalı**, ürün yüzeyinden açılamaz, ama **açık yolu da uygulanmış ve testlidir** (uygulanmamış dal bırakılmaz, K6).
- Parolalar yalnızca güçlü bir hash ile (BCrypt / Argon2) saklanır; şifre sıfırlama token'ları tek kullanımlık, kısa ömürlü ve hash'li saklanır. Giriş, kayıt ve sıfırlama uçları hız sınırlıdır.
- Dosya yükleme (ürün fotoğrafı): tip ve boyut sunucuda doğrulanır, dosya adı yeniden üretilir, içerik uygulama kökünden servis edilmez.
- CORS yalnızca bilinen `web` kaynağına açılır (ortam değişkeni); `*` **yasaktır**. Çerez tabanlı oturum kullanılıyorsa `HttpOnly`, `Secure`, `SameSite` ayarlanır ve CSRF korunur.

### 11.7 Test (api)

- Araç ([ADR-005](docs/adr/005-test-strategy.md)): JUnit 5 + AssertJ, `@SpringBootTest` / `MockMvc`, repository testleri **Testcontainers PostgreSQL** ile (H2 ile sapma kabul edilmez).
- Etiketler: `critical` (para, stok, sipariş durumu, yetkilendirme, sözleşme — hızlı ve her zaman koşar), `integration` (DB / dış sınır). Yeni iş kuralı **mutlaka** `critical`.
- Test adı cümle gibi (`satici_baskasinin_siparisini_goremez`); `@DisplayName` senaryonun spec/ADR karşılığını söyler. Ortak kurulum için küçük fabrikalar (`aSeller()`, `anOrder()`); sabit `Clock`.
- Testler **dış dünyaya çıkmaz**: e-posta mailpit/bellek, ödeme ve kargo `fake` / sandbox sağlayıcı. Gerçek SMTP / ödeme / kargo'ya giden test **yasaktır** (K10).
- Her yeni endpoint için: mutlu yol + doğrulama hatası + yetkisiz (401) + başkasının kaydı (404/403) testi.

## 12. `web/` Kuralları (Next.js)

### 12.1 Rol ve sınırlar

`web/`, alıcı vitrini ve satıcı panelidir; **sunum katmanıdır.** Yasak: DB erişimi, SQL, ORM; dış servise doğrudan çağrı; iş kuralı veya para hesabı (fiyat, toplam, komisyon, kargo ücreti, stok kararı sunucudan gelir — ekran gösterir); istemcide yetkilendirme kararı (UI gizlemek yetkilendirme değildir, otorite `api/`'dir); sözleşmede olmayan bir alanı varsayma.

### 12.2 Yerleşim

- `web/src/app/` App Router: rota grupları `(shop)` (vitrin; SSR/ISR, SEO) ve `(seller)` (satıcı paneli; oturum gerektirir, dinamik). Her rota için `page.tsx` + gereken yerde `loading.tsx`, `error.tsx`, `not-found.tsx` — **dört durum zorunludur** (§12.5).
- `web/src/features/<domain>/` domain'e göre: `components/`, `api.ts` (bu domain'in veri çekme fonksiyonları), `types.ts` (yalnızca üretilen tiplerden türetilenler), `*.test.tsx`.
- `web/src/components/ui/` yalnızca gerçekten paylaşılan sunum bileşenleri; ikinci kullanım ortaya çıkmadan buraya taşınmaz (K9).
- `web/src/lib/` çapraz kesen: `apiClient.ts` (tek HTTP katmanı), `money.ts` (minor unit → gösterim), `datetime.ts`, `env.ts` (doğrulanmış ortam okuma).
- Veri çekme **sunucu tarafında** (Server Component / Route Handler) varsayılandır; `"use client"` yalnızca etkileşim gerektiren yaprak bileşende ve gerekçeli.

### 12.3 TypeScript ve veri katmanı

- `strict: true`; `any` ve `as` ile tip zorlaması **yasaktır** (bilinmeyen veri `unknown` + daraltma). `@ts-ignore` yasak, `@ts-expect-error` yalnızca gerekçeli yorumla.
- API tipleri **elle yazılmaz**: `shared/generated` üretilen tiplerinden import edilir (K8). Sözleşmede olmayan alan UI'da olamaz; ihtiyaç varsa önce sözleşme (K2).
- Tüm HTTP çağrıları tek `lib/apiClient.ts` üzerinden: base URL ortamdan, kimlik bilgisi tek yerde, §11.5 hata gövdesi tek yerde çözülür (`code` → kullanıcı mesajı, `fieldErrors` → form alanları). İkinci bir `fetch` sarmalayıcı yazılmaz.
- **Token'lar ince bir BFF katmanında yaşar ([ADR-008](docs/adr/008-jwt-auth-with-opaque-refresh.md)):** giriş/çıkış/yenileme `web`'in kendi Route Handler'larından geçer; erişim ve yenileme token'ı `HttpOnly` + `Secure` + `SameSite=Lax` çerezlere yazılır. Token tarayıcı JavaScript'ine **hiç** verilmez: `localStorage`, `sessionStorage` ve istemci state'inde token tutmak **yasaktır**. Sunucu tarafı render çerezi okur ve `api`'ye `Authorization: Bearer` ile gider; durum değiştiren Route Handler çağrıları CSRF token'ı taşır.
- Gizli değer tarayıcıya gitmez: sunucu tarafı `process.env`, istemciye yalnızca `NEXT_PUBLIC_` ile açıkça işaretlenmiş ve gizli olmayan değerler (K10).
- Form doğrulaması **erken geri bildirimdir**; otorite `api/`'dir. Sunucunun döndürdüğü hata mesajı olduğu gibi gösterilir ve ilgili alana bağlanır.
- Para ve tarih biçimlendirmesi tek yardımcıda (`money.ts`, `datetime.ts`), `Intl` ile; bileşen içinde elle biçimlendirme yapılmaz.

### 12.4 Stil ve metin

- Stil yöntemi ([ADR-027](docs/adr/027-shadcn-ui-design-system.md)): Tailwind v4 + shadcn/ui. Tasarım token'ları tek dosyada (`web/src/app/globals.css`): ham renk merdivenleri (`--green-*`, `--neutral-*` …) yalnızca orada kullanılır; bileşen ve sayfa yalnızca anlamsal rol sınıflarını kullanır (`primary`, `secondary`, `muted`, `accent`, `destructive`, `success`, `warning`, `info` ve `*-foreground` eşleri; [ADR-028](docs/adr/028-theme-choice-and-color-roles.md)), sabit renk (`bg-green-700`, `#fff`) yazmaz. Yeni rol `-foreground` eşiyle ve iki temada AA kontrastı hesaplanarak eklenir. Tema Açık / Koyu / Sistem seçicisiyle değişir; tercih çerezde, sunucu `<html data-theme>` basar. Temel bileşenler `web/src/components/ui/` altındadır; yeni bileşen [UI bileşeni ekleme](docs/how-to/ui-bileseni-ekleme.md) rehberiyle eklenir ve varsayılan halde bırakılmaz (dokunma hedefi mobilde 44px). Koşullu sınıf yalnızca `cn()` (`@/lib/utils`) ile kurulur. Döküm: [`docs/reference/03-design-system.md`](docs/reference/03-design-system.md). Bütçe dışı UI kütüphanesi, ikinci ikon seti, hareket kütüphanesi ve CDN **ADR gerektirir** (K7); ihtiyaç mimari kararı yeniden değerlendirme tetikleyicisidir, `npm install` değil.
- Mobil önce; tek ek kırılım (`min-width: 1024px`). Satıcılar sahada telefonla çalışır: satıcı paneli mobilde eksiksiz çalışır, "masaüstünde kullanın" uyarısı kabul edilmez.
- `[hidden] { display: none !important }` globaldir; görünürlük `hidden` / koşullu render ile sağlanır. Inline `style` ve `onclick` yasak; JS'in belirlediği düzen değerleri CSS özel özelliği olarak geçirilir.
- Kullanıcıya görünen metinler `web/messages/tr.json` (kaynak dil) ve `en.json` içindedir, ICU biçiminde (çoğul, seçim, yer tutucu); bileşen `useTranslations` / `getTranslations` ile okur ([ADR-029](docs/adr/029-next-intl-without-routing.md)). Bileşen içine serpiştirilmiş hardcode metin **yasaktır**. Yeni anahtar iki dosyaya birlikte eklenir (anahtar eşitliği testle zorunlu). Dil URL'de görünmez: çerez → tarayıcı dili → Türkçe; seçili dil `api`'ye `Accept-Language` ile iletilir ([ADR-030](docs/adr/030-locale-from-cookie-then-header.md)). Para ve sayı `Intl` ile seçili dilde biçimlenir. Ürün ve satıcı adı gibi kullanıcı verisi çevrilmez.
- Erişilebilirlik: her input etiketli; akış klavyeyle tamamlanır; odak görünür; renk tek başına anlam taşımaz; kontrast WCAG AA. 375 ve 1440 px'de doğrulanır.
- Görseller `next/image` ile, boyut ve `alt` zorunlu; ürün fotoğraflarında `alt` ürün adından türetilir.

### 12.5 Dört durum ve UX ölçütü

Her ekranın dört durumu vardır ve tasarlanır: **yükleniyor** (iskelet) · **boş** (yönlendirici boş durum; boş beyaz sayfa **yasak**) · **hata** (ne oldu + ne yapmalı + yeniden dene) · **yetkisiz** (giriş / izin yönlendirmesi). Bir bileşenin hatası kendi kutusunda kalır; sayfa tümden çökmez (`error.tsx` + sınır bileşenleri).

Ölçüt estetik değil **bilişsel yük, işlem süresi, hata payı**: seçenek sayısını sınırla ve ileri ayarları katla (Hick), birincil eylem büyük ve sabit / yıkıcı eylem uzak ve onaylı (Fitts), alışılmış e-ticaret desenleri (Jakob), formu küçük gruplara böl (Miller). Satıcı için kritik akış — ürün ekleme ve kargo bildirimi — en kısa yoldur; para ve stok etkileyen her eylem sonucunu açıkça söyler.

### 12.6 Test (web)

- Vitest + Testing Library: davranış testi (rol / erişilebilir ad ile sorgulama), snapshot testi değil. Her form için: doğrulama hatası, sunucu hatası eşlemesi, başarı yolu.
- Playwright: yalnızca gerçek tarayıcı gerektiren kritik akışlar (ürün ekleme, sepet → sipariş, kargo bildirimi). Tarayıcı konsolu temiz olmalı: `console.log`, `debugger`, `alert()` bırakılmaz (K6).
- Ağ her zaman mock'lanır veya ayağa kalkmış test ortamına gider; test üretime / gerçek sağlayıcıya çıkmaz (K10).

## 13. Git ve PR

- Branch: `<TICKET>-kisa-aciklama` veya `hotfix/…`, `docs/…`. `main`'e doğrudan commit **yok**.
- Commit mesajı **İngilizce**, emir kipi, tek satır özet: `type(scope): summary`; `type` ∈ {`feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`}; `scope` isteğe bağlı ∈ {`api`, `web`, `shared`, `docs`, `adr`, `infra`}. Gövde gerekiyorsa kısa ve madde madde. Changeset, model değişikliğiyle **aynı** commit'te.
- PR açıklaması zorunlu başlıklar: **Doküman / ADR** · **Sözleşme değişikliği** · **Etki matrisi** · **Doğrulama** (komutlar + gerçek sonuç) · **Güvenlik / kişisel veri etkisi** · **Bitmeyen işler / açık sorular.**

## 14. Rapor Formatı

1. Değişen dosyalar (yol + tek satır özet).
2. Çalıştırılan komutlar ve **gerçek** çıktıları (başarısızsa başarısız; çalıştırılamadıysa neden).
3. Etki matrisi (`shared/` veya DB değiştiyse).
4. Test etkisi (§6.3) ve doküman etkisi (§6.4) cevapları.
5. Güvenlik / yetkilendirme / kişisel veri etkisi (§11.6, K10).
6. Task dışı değişiklikler ve gerekçeleri; yeni yazılan her yardımcı için "mevcut X neden yetmedi" (K9).
7. Onay bekleyen kararlar; `KALANLAR.md`'ye eklenen açık sorular.
8. Bitmeyen işler ve kaldığın nokta (K6).

## 15. Açık Kararlar

**Tek liste: [`openspec/KALANLAR.md`](openspec/KALANLAR.md).** Bu bölüm o listenin kopyasını tutmaz; iki yerde tutulan liste iki farklı gerçeğe dönüşür.

Kural: bir madde karara bağlanmadan ilgili alanda **kod yazılmaz**; karar verilmeden iki alternatifi birlikte uygulamak (iki kimlik yolu, iki ödeme akışı) **yasaktır**. Karara bağlanan madde `docs/adr/` altına ADR olur, `KALANLAR.md`'den silinir ve `docs/features/_genel.md` *Tarihçe*'sine satır düşer.

Bugün bloke eden maddeler (özet; gerekçeler KALANLAR dosyasında):

| Kod | Konu | Neyi blokluyor |
|---|---|---|
| A1 | Ödeme sağlayıcısı seçimi (model, komisyon, iade karara bağlandı: ADR-054…056) | Gerçek sağlayıcı uygulaması, canlıda ödeme |
| A2 | Kargo entegratörü seçimi (entegrasyon biçimi, ücret, süre karara bağlandı: ADR-057, ADR-058) | Gerçek entegratör uygulaması, canlıda gönderi |
| A4 | MVP kapsamı: mobil uygulama (çoklu dil ADR-029…032 ve teslimat bölgesi ADR-063 ile karara bağlandı) | §3 over-engineering sınırı |
| A5 | Satıcı verisinin saklama süresi, KVKK aydınlatma metni, belgenin yasal yeterliliği | Belge silme işi, başvuru formundaki onay kutusu |
| B4 | Bildirim kanalları (e-posta / SMS) | `notification`, K7 bütçesi |
| B5 | Üretim barındırma ve yedekleme | CI/CD, üretim compose varyantı |

**Karara bağlananlar:** [ADR-001](docs/adr/001-spring-api-and-next-web.md) (iki birim) · [ADR-002](docs/adr/002-dependency-budget.md) (bağımlılık bütçesi) · [ADR-003](docs/adr/003-openapi-as-single-contract.md) (tek sözleşme) · [ADR-004](docs/adr/004-postgresql-with-flyway.md) (PostgreSQL + Flyway) · [ADR-005](docs/adr/005-test-strategy.md) (test stratejisi) · [ADR-006](docs/adr/006-money-as-minor-unit.md) (para temsili) · [ADR-007](docs/adr/007-query-level-ownership-scoping.md) (satıcı izolasyonu) · [ADR-008](docs/adr/008-jwt-auth-with-opaque-refresh.md) (kimlik: JWT + iptal edilebilir yenileme, tek hesap tablosu, zorunlu doğrulama) · [ADR-009](docs/adr/009-contract-generated-from-spring.md) (sözleşme Spring'den üretilir) · [ADR-037](docs/adr/037-seller-application-with-document-and-manual-approval.md), [ADR-038](docs/adr/038-listings-publish-immediately-with-admin-takedown.md) (A3: satıcı başvurusu + manuel onay, ilan ön onaysız) · [ADR-039](docs/adr/039-uploads-on-local-volume-served-by-api.md) (B3: dosyalar yerel volume'da, `api` sunar) · [ADR-054](docs/adr/054-split-payment-via-provider-port-provider-pending.md)…[ADR-056](docs/adr/056-cancel-before-shipment-and-defect-refund.md) (A1: bölünmüş ödeme, komisyon, iptal ve iade) · [ADR-057](docs/adr/057-shipping-via-integrator-port-integrator-pending.md), [ADR-058](docs/adr/058-flat-shipping-fee-per-seller-and-declared-handover.md) (A2: kargo entegratörü, ücret ve kargoya verme süresi) · [ADR-063](docs/adr/063-delivery-provinces-config-address-book.md) (A4: teslimat bölgesi).

> [!NOTE]
> Bu dosya değiştiğinde `docs/features/_genel.md` *Tarihçe*'sine bir satır düşer (K1).

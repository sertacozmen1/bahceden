# [ADR-009] Sözleşme Spring kodundan üretilir; `openapi.yaml` üretilmiş ama commit'lenen bir çıktıdır

**Tarih:** 2026-10-06
**Durum:** Accepted

---

## Context

[ADR-003](003-openapi-as-single-contract.md) `api/` ile `web/` arasındaki tek arayüzün OpenAPI dosyası olduğunu sabitledi ama bir soruyu bilinçli olarak açık bıraktı: bu dosya **elle mi yazılacak** (sözleşme kaynak, kod ona uyar) yoksa **Spring kodundan mı üretilecek** (kod kaynak, sözleşme çıktı)? Soru `openspec/KALANLAR.md` B2 olarak izlendi ve ilk dilimi blokluyordu.

Kararın alındığı andaki kısıtlar:

- Aynı küçük ekip iki tarafı da yazıyor; elle yazılan sözleşme ile kod arasındaki senkronu koruyan tek şey disiplin olurdu.
- Elle yazılan sözleşme, her alan değişikliğinde iki dosyaya dokunmak demek: pratikte en sık atlanan adım.
- `api/` zaten tek yazma otoritesi ([ADR-001](001-spring-api-and-next-web.md)): DTO'lar, Bean Validation kısıtları ve hata gövdesi orada tanımlı. Aynı bilgiyi YAML'da ikinci kez yazmak çift kaynak demek.
- `web`'in kaybetmemesi gereken şey tipin güncelliği; sözleşmenin **kim tarafından yazıldığı** değil.

## Decision

**Sözleşme Spring kodundan üretilir.** Kaynak `api/` içindeki controller'lar, DTO `record`'ları ve Bean Validation anotasyonlarıdır; `shared/contracts/openapi.yaml` bunların **üretilmiş çıktısıdır.**

1. **Üretim mekanizması:** `api/` bağımlılık bütçesine `springdoc-openapi-starter-webmvc-api` eklenir (Swagger UI paketi **değil**, yalnızca spec üretimi). Spec, bir `@SpringBootTest` içinde `/v3/api-docs.yaml` çağrılarak alınır ve `shared/contracts/openapi.yaml` dosyasına yazılır.
2. **Dosya commit'lenir.** Üretilmiş olmasına rağmen depoda tutulur: PR diff'inde sözleşme değişikliği **görünür** olmalı. Kırıcı bir değişiklik (alan silme, yeniden adlandırma, zorunluluk ekleme) review'da gözle yakalanır ve kullanıcı onayına tabidir (`AGENTS.md` §9).
3. **Tazelik CI'da zorunlu.** Aynı test `--check` modunda çalışır: üretilen spec ile commit'lenen dosya birebir aynı değilse **test kırmızıdır**. Kod değişip sözleşme güncellenmemiş bir commit CI'dan geçemez.
4. **`openapi.yaml` elle düzenlenmez** (`AGENTS.md` K8). Çıktı yanlışsa kaynağı, yani Spring kodu ve anotasyonları düzeltilir.
5. **`web` tiplerini bu dosyadan üretir** (`shared/generated/`, gitignore'da) ve API tipini elle yazmaz. Sözleşmede olmayan bir alan UI'da olamaz; akış: `api` (DTO + anotasyon) → `openapi.yaml` → üretilen tipler → `web`.
6. **Sözleşme kalitesi kodun sorumluluğudur.** Türkçe `description` ve `example` değerleri `@Schema` anotasyonlarıyla DTO'larda verilir; sayfalama, hata gövdesi ve para alanları tüm uçlarda aynı tipleri kullanır (`AGENTS.md` §11.5). "Üretilmiş spec zayıf olur" riski anotasyon disipliniyle karşılanır ve review'da bu disipline bakılır.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Elle yazılan `openapi.yaml` (sözleşme kaynak) | Saf contract-first disiplini ve dilden bağımsız tasarım sunuyor; ancak aynı ekip iki tarafı yazdığı için çift kaynak pratikte ayrışır ve ayrışmayı yakalayan tek mekanizma elle yazılmış doğrulama testleri olurdu. Üretim + tazelik kontrolü aynı güveni daha az elle işle veriyor. |
| `springdoc-openapi-maven-plugin` ile build sırasında üretim | Spec'i üretmek için uygulamayı ayağa kaldırıyor; derleme hattına ikinci bir eklenti ve ayrı bir yaşam döngüsü ekliyor. `@SpringBootTest` ile üretim hem bütçeye bir şey eklemiyor hem tazelik kontrolünü doğal olarak teste bağlıyor. |
| Üretilen dosyayı commit'lememek (yalnızca CI'da üretmek) | Sözleşme değişikliği PR diff'inde görünmez olurdu; kırıcı değişiklik gözden kaçardı. Üretilmiş dosyanın commit'lenmesi burada bilinçli bir review aracıdır. |
| `springdoc-openapi-starter-webmvc-ui` (Swagger UI dahil) | Swagger UI geliştirmede konforlu ama çalışma zamanına bir UI ve ek bağımlılık ağı taşıyor; ihtiyaç doğarsa ayrı kararla eklenir. |

## Bağımlılık bütçesi etkisi

Bu karar [ADR-002](002-dependency-budget.md) bütçesini **bir kalemle genişletir**: `api/` runtime bağımlılıklarına `springdoc-openapi-starter-webmvc-api` eklenir. ADR-002 superseded **değildir**; bütçenin "ihtiyaç kanıtlandığında ADR ile büyür" maddesi işletilmiştir. K7'nin dört sorusunun cevabı:

1. **Platform neden yetmiyor?** Spring Boot OpenAPI spec üretmiyor; elle yazmak çift kaynak demek (yukarıda reddedildi).
2. **Alternatifler:** elle yazılan spec (reddedildi: ayrışma riski) ve maven eklentisi (reddedildi: ikinci yaşam döngüsü).
3. **Yük:** tek artifact, Spring Boot sürümüyle hizalı, aktif bakımda, Apache-2.0; UI paketi alınmadığı için transitive ağ küçük; çalışma zamanı etkisi yalnızca `/v3/api-docs` uçları (üretimde kapatılabilir).
4. **Katman:** `api/`; bağımlılık yönünü bozmaz — sözleşme hâlâ `api`'den `web`'e doğru akar.

## Consequences

### Olumlu
- Kod ile sözleşme ayrışamaz: tazelik kontrolü CI'da zorunlu, çift kaynak yok.
- Alan eklemek tek yerde iş: DTO'ya alan + `@Schema` açıklaması; spec ve `web` tipleri kendiliğinden gelir.
- Sözleşme değişikliği PR diff'inde görünür kalıyor; kırıcı değişiklik review'da yakalanıyor.

### Olumsuz
- **Felsefe kayması:** `AGENTS.md` §1'deki "contract-first" artık "sözleşme elle tasarlanır" anlamında değil; tasarım kararı DTO'da alınır, sözleşme onu yansıtır. Kural metni buna göre güncellendi (K2). Disiplin kaybı riski gerçektir: alan adı ve zorunluluk kararları artık Java tarafında, acele edilince kötü sözleşme üretilebilir.
- Üretilen spec'in okunabilirliği anotasyon disiplinine bağlı: `@Schema` yazılmazsa açıklamasız, örneksiz bir sözleşme çıkar. Review bu noktaya bakmak zorunda.
- Java tip sisteminin izi sözleşmeye sızabilir (ör. sarmalayıcı sınıf adlarının şema adı olması); şema adlarının anlamlı kalması için anotasyonla müdahale gerekir.
- `openapi.yaml` artık üretilmiş dosya: elle düzeltme refleksi K8 ihlali olur ve CI'da kırmızıya düşer.

### Nötr / Trade-off
- Dilden bağımsız tasarım avantajı (sözleşmeyi önce masada tasarlamak) kaybedildi; bunun karşılığında senkron garantisi alındı. İleride üçüncü parti istemciye açık, sürümlenmiş bir kamu API'si gerekirse bu karar yeniden değerlendirilir.

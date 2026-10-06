# [ADR-005] Test stratejisi: Testcontainers ile gerçek PostgreSQL, `critical` etiketi, tarayıcı testi yalnızca kritik akışlarda

**Tarih:** 2026-10-06
**Durum:** Accepted

---

## Context

Bahçeden'de yanlış davranışın bedeli para: hatalı fiyat, eksik stok düşümü, başkasının siparişini görebilen satıcı, iki kez tahsil edilen ödeme. Bu davranışların testle sabitlenmesi gerekiyor.

Aynı zamanda test paketi hızlı kalmalı; her commit'te dakikalar süren bir paket, atlanan bir pakete dönüşür. İki uygulama ([ADR-001](001-spring-api-and-next-web.md)) olduğu için iki ayrı test düzeni kurulacak ve ikisi de aynı disiplini paylaşmalı.

## Decision

**`api/` tarafı:**

- JUnit 5 + AssertJ; web katmanı `MockMvc`; güvenlik testleri `spring-security-test`.
- Repository ve akış testleri **Testcontainers PostgreSQL** ile gerçek veritabanına karşı koşar. H2 kullanılmaz ([ADR-004](004-postgresql-with-flyway.md), [ADR-016](016-liquibase-for-schema-migrations.md)).
- Etiketler: **`critical`** (para, stok, sipariş durumu, yetkilendirme, sözleşme uyumu — hızlı, her zaman koşar) ve **`integration`** (veritabanı ve dış sınır).
- **Yeni iş kuralı = `critical` etiketli test.** Para, stok, sipariş durumu ve yetkilendirme her zaman bu kapsamdadır.
- Her yeni endpoint için asgari dört test: mutlu yol, doğrulama hatası, kimliksiz erişim (401), **başkasının kaydına erişim** (404/403) ([ADR-007](007-query-level-ownership-scoping.md)).
- "Şimdi" enjekte edilen `Clock` ile alınır; zamana bağlı davranış testte sabitlenir.

**`web/` tarafı:**

- Vitest + Testing Library ile davranış testi (rol ve erişilebilir ad ile sorgulama). Snapshot testi kullanılmaz: kırılgan ve düşük bilgi veriyor.
- Her form için üç test: doğrulama hatası, sunucu hatasının alanlara eşlenmesi, başarı yolu.
- **Playwright yalnızca gerçek tarayıcı gerektiren kritik akışlarda**: ürün ekleme, sepet → sipariş, kargo bildirimi. Her ekran için tarayıcı testi yazılmaz.

**Her iki taraf:**

- Testler **dış dünyaya çıkmaz**: e-posta `mailpit`/bellek, ödeme ve kargo `fake`/sandbox sağlayıcı, ağ istekleri mock. Gerçek SMTP, ödeme veya kargo çağrısı yapan test **yasaktır** (AGENTS.md K10).
- Test verisi sahtedir (`ornek.test`, `+90 555 000 00 00`); gerçek müşteri verisi fixture'a girmez.
- `@Disabled` / `it.skip` ile susturulmuş test bırakılmaz (AGENTS.md K6).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| H2 ile hızlı repository testleri | Motor farkı (tip davranışı, kilitleme, `jsonb`) testi yalancı yeşile çeviriyor; para ve stok davranışını yanlış doğruluyor. |
| Her ekran için Playwright testi | Çok yavaş ve kırılgan; aynı güveni Vitest + `MockMvc` çok daha ucuza veriyor. Tarayıcı testi gerçekten tarayıcıya ihtiyaç duyan akışlara saklanıyor. |
| Yüzde cinsinden kapsam hedefi (örn. %80 coverage) | Sayıyı tutturmak için anlamsız test yazmaya itiyor. Hedef kapsam yüzdesi değil, **davranışın sabitlenmesi**: `critical` etiketi bu niyeti taşıyor. |
| Üretime yakın entegrasyon testi (gerçek ödeme sandbox'ı CI'da) | CI'yı dış servis kesintisine bağımlı ve yavaş yapıyor; sağlayıcı sözleşmesi `fake` sağlayıcıyla taklit edilip elle doğrulanıyor. |

## Consequences

### Olumlu
- Para ve yetkilendirme davranışı testle kilitli; regresyon commit'te yakalanıyor.
- Veritabanı testleri üretimle aynı motora karşı: anlamlı sonuç.
- Test paketi odaklı kalıyor; `critical` etiketi hızlı geri bildirim veriyor.

### Olumsuz
- Testcontainers testleri Docker gerektiriyor ve birim testlerden yavaş; CI süresi artıyor.
- Dört test kuralı her endpoint için ek yazma işi demek.

### Nötr / Trade-off
- Dış servisler `fake` sağlayıcıyla taklit ediliyor: sözleşme değişikliği testle değil, elle doğrulanıyor. Bu bilinçli bir boşluk ve ilgili how-to sayfasında yazılı olmalı.

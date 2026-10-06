# [ADR-003] `api/` ile `web/` arasındaki tek sözleşme OpenAPI dosyasıdır

**Tarih:** 2026-10-06
**Durum:** Accepted

---

## Context

[ADR-001](001-spring-api-and-next-web.md) iki ayrı dağıtılabilir birim kurdu. İki birim arasındaki arayüz belirsiz kalırsa klasik sorun başlar: istemci alan adlarını tahmin eder, sunucu bir alanı yeniden adlandırır, hata üretimde ortaya çıkar. Elle yazılmış TypeScript tipleri bir hafta sonra gerçeği yansıtmaz.

MVP'de aynı ekip iki tarafı da yazacak; bu, sözleşmeyi atlama cazibesini artırır. Kuralın araçla desteklenmesi gerekir, yoksa niyet olarak kalır.

## Decision

`shared/contracts/openapi.yaml` (OpenAPI 3.1) iki birim arasındaki **tek** sözleşmedir ve commit'lenir:

- `web/` kullandığı API tiplerini bu dosyadan **üretir** (`shared/generated/`, gitignore'da); API tipi elle yazılmaz.
- `api/` her endpoint için sözleşmeye karşı bir `MockMvc` testi tutar: alan adları, zorunluluklar ve hata gövdesi sözleşmeyle eşleşmezse test kırmızı olur.
- Sözleşmede olmayan bir alan UI'da **olamaz**; ihtiyaç doğarsa akış şöyledir: sözleşme → üretilen tipler ve referans dokümanı → `api` → `web`.
- Sözleşme hiçbir katmana özgü bilgi içermez: Java sınıf adı, tablo adı, React bileşeni sözleşmede geçmez.
- Sayfalama, sıralama, hata gövdesi ve para alanları **tüm uçlarda aynı** şekli kullanır (AGENTS.md §11.5).
- Kırıcı değişiklik (alan silme, yeniden adlandırma, tip değiştirme) kullanıcı onayı gerektirir; geçiş `deprecated` işaretiyle yapılır.

Sözleşmenin **elle mi yazılacağı yoksa Spring anotasyonlarından mı üretileceği** bu ADR'nin kapsamı dışındadır ve açık bir karardır (`openspec/KALANLAR.md` B2). Bu ADR yalnızca "iki birim arasındaki tek sözleşme OpenAPI dosyasıdır ve ondan sapılamaz" kararını sabitler.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Sözleşme yok; istemci `fetch` ile ne bulursa onu kullanır | Sessiz kırılma makinesi: alan adı değişikliği derlemede değil kullanıcıda patlıyor. |
| Elle yazılmış paylaşılan TypeScript tipleri | İki yerde tutulan gerçek bir süre sonra iki farklı gerçek olur; Java tarafıyla senkronu denetleyen hiçbir mekanizma yok. |
| GraphQL | Şema avantajı var ama MVP'de kazanç yok: tek istemci, sabit ekranlar. N+1, yetkilendirme ve önbellek karmaşıklığını erken ithal ederdi; [ADR-002](002-dependency-budget.md) bütçesini de büyütürdü. |
| Ortak Java/TS kod modülü (`common/`) | [ADR-001](001-spring-api-and-next-web.md)'in bağımlılık yönünü bozar; iki dilde tek kaynak zaten mümkün değil. |

## Consequences

### Olumlu
- Alan adı ve zorunluluk uyumsuzluğu **derleme/test zamanında** yakalanıyor, üretimde değil.
- İstemci tipleri her zaman güncel; elle senkron yükü yok.
- Sözleşme aynı zamanda dokümantasyon: `docs/reference/` API referansı buradan beslenir.

### Olumsuz
- Her alan değişikliği ek bir adım (sözleşme + üretim) demek; küçük işler biraz yavaşlıyor.
- Üreteç ve doğrulama hattının kurulması ve bakımı bir maliyet.

### Nötr / Trade-off
- Üretilen dosyalar commit'lenmiyor (CI'da üretilir); bu depoyu temiz tutuyor ama CI'ya bir adım ekliyor.

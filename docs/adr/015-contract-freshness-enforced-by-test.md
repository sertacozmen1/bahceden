# [ADR-015] Sözleşme tazeliği bir testle zorunludur; dosya testten üretilir

**Tarih:** 2026-10-06
**Durum:** Accepted
**Kaynak:** `add-storefront-product-listing` change, `design.md` D6

---

## Context

[ADR-009](009-contract-generated-from-spring.md) sözleşmenin Spring kodundan üretildiğini ve `shared/contracts/openapi.yaml` olarak commit'lendiğini söylüyor. Bu kararın tek zayıf noktası var: üretilmiş dosya **bayatlayabilir.** DTO'ya alan eklenir, sözleşme yeniden üretilmez, `web` tipleri eski kalır ve kimse fark etmez.

Üretim mekanizması da bir seçim gerektiriyordu: derleme eklentisi mi, test mi?

## Decision

Sözleşme bir **test** tarafından üretilir ve doğrulanır:

- Test, uygulama bağlamını kaldırıp `/v3/api-docs.yaml` çıktısını alır.
- Varsayılan modda çıktıyı commit'lenen dosyayla **birebir** karşılaştırır; fark varsa **kırmızıdır** ve hata mesajı ne yapılacağını söyler.
- `-Dopenapi.write=true` ile çalıştırıldığında dosyayı günceller. Geliştirici akışı: DTO'yu değiştir, bu komutu çalıştır, üretilen diff'i commit'e dahil et.
- Satır sonu farkları (CRLF/LF) sözleşme farkı sayılmaz.

Dosya **elle düzenlenmez** (`AGENTS.md` K8); çıktı yanlışsa DTO ve anotasyonlar düzeltilir.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| `springdoc-openapi-maven-plugin` ile derleme sırasında üretim | Spec üretmek için uygulamayı ayağa kaldırıyor, derleme hattına ikinci bir eklenti ve ayrı bir yaşam döngüsü ekliyor. Test ile üretim bütçeye hiçbir şey eklemiyor ve tazelik kontrolünü doğal olarak teste bağlıyor. |
| Her derlemede dosyayı sessizce üzerine yazmak | Sözleşme değişikliği PR diff'inde fark edilmeden geçer; kırıcı değişiklik gözden kaçar. |
| Tazeliği yalnızca CI'da ayrı bir adımla kontrol etmek | Geliştirici aynı kontrolü yerelde koşamaz; geri bildirim CI'ya ertelenir. Test, her iki yerde aynı mekanizma. |
| Üretilen dosyayı commit'lememek | Review yüzeyi kaybolur ([ADR-009](009-contract-generated-from-spring.md) gerekçesi). |

## Consequences

### Olumlu
- Kod ile sözleşme ayrışamaz: ayrışma anında ve yerelde kırmızı.
- Üretim ve doğrulama tek mekanizma; öğrenilecek ikinci bir araç yok.
- Hata mesajı yolu gösterdiği için "elle düzelt" refleksi engelleniyor.

### Olumsuz
- Testin çalışması için uygulama bağlamı ve veritabanı konteyneri gerekiyor: hızlı bir birim testi değil, `integration` etiketli.
- Üretilen çıktının alan sırası springdoc sürümüne bağlı; sürüm yükseltmesi sözleşmede gürültülü bir diff üretebilir (gerçek bir değişiklik olmadan).
- Geliştiricinin fazladan bir komut hatırlaması gerekiyor; hata mesajı bu yükü azaltıyor ama ortadan kaldırmıyor.

### Nötr / Trade-off
- Satır sonu normalizasyonu yapıldığı için Windows ve Linux geliştiricileri aynı dosyayı üretir; bunun dışında hiçbir "akıllı" karşılaştırma yok: biçimsel fark da gerçek fark sayılır.

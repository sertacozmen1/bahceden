# [ADR-014] Geliştirme verisi, dev profiline bağlı tekrarlanabilir bir Flyway betiğiyle gelir

**Tarih:** 2026-10-06
**Durum:** Accepted
**Kaynak:** `add-storefront-product-listing` change, `design.md` D5

---

## Context

`AGENTS.md` K6 kodda hardcode mock veriyi yasaklıyor, ama bir boşluk bırakıyor: ekranın dolu halini görmeden dört durum (yükleniyor, boş, hata, yetkisiz) doğrulanamaz ve geliştirici her açılışta elle veri girmek zorunda kalır.

İhtiyaç: geliştirmede her zaman hazır, tekrarlanabilir, üretime asla bulaşmayan örnek veri.

## Decision

Geliştirme verisi bir **tekrarlanabilir** Flyway betiğiyle (`R__dev_seed_catalog.sql`) gelir ve bu betik yalnızca `dev` profilinde Flyway konumlarına eklenir (`application-dev.yaml` içinde `spring.flyway.locations`). Üretim profilinde dosya hiç görülmez.

Kurallar:

- **Idempotent**: `on conflict do nothing`; tekrar çalıştığında kopya üretmez.
- **Tamamen sahte**: gerçek üretici adı, adres, telefon veya e-posta içermez (`AGENTS.md` K10). Örnek alan adı `ornek.test`.
- **Negatif örnekler dahil**: vitrinde görünmemesi gereken `DRAFT` ve `ARCHIVED` kayıtlar bilinçli olarak seed'de durur; süzgecin ve boş durumun elle doğrulanması bunu gerektirir.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Bileşen veya servis içinde sabit kodlanmış örnek dizi | K6 ihlali: mock veri üretim koduna karışır ve silinmesi unutulur. |
| Sürümlenmiş (`V__`) migration ile seed | Üretim veritabanına da uygulanır; sahte kayıt üretim verisine girer. |
| Profil kontrolü olmadan `R__` betiği | Aynı sorun: Flyway tüm ortamlarda çalıştırır. |
| Uygulama açılışında Java tabanlı seed (`ApplicationRunner`) | Veri kurulumu uygulama koduna taşınır ve koşul kontrolü (`@Profile`) unutulduğunda üretime sızar; SQL betiği hem daha okunabilir hem profil ayrımı yapılandırmada. |
| Testlerin seed verisine dayanması | Testler kendi verisini kurar; paylaşılan seed'e dayanan test, seed değişince kırılır ve neden kırıldığı anlaşılmaz. |

## Consequences

### Olumlu
- Geliştirici `docker compose up` sonrası dolu bir vitrin görür; dört durumun hepsi gerçek veriyle denenebilir.
- Üretim verisine sahte kayıt girme yolu yapılandırma seviyesinde kapalı.
- Seed, süzgeç davranışının canlı belgesi: yayınlanmamış kayıtlar bilinçli olarak orada.

### Olumsuz
- Seed ile şema birlikte değişmek zorunda: tabloya zorunlu bir kolon eklendiğinde betik de güncellenmeli, yoksa dev ortamı kırılır.
- Tekrarlanabilir betik her Flyway çalışmasında (checksum değiştiğinde) yeniden koşar; ağır bir seed ileride açılışı yavaşlatabilir.

### Nötr / Trade-off
- Testler seed'den bağımsız olduğu için seed yalnızca insan doğrulaması içindir; otomatik testlerde hiçbir güvence sağlamaz.

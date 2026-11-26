## ADDED Requirements

### Requirement: Hız sınırı uygulama örnekleri arasında paylaşılır

Sistem, hız sınırı sayaçlarını tüm uygulama örneklerinin ortak kullandığı bir depoda SHALL tutar. Aynı istemci ve aynı uç ailesi için sınır, istek hangi örneğe giderse gitsin toplamda uygulanır; bir örneğin yeniden başlatılması sayaçları sıfırlamaz. Sayaç anahtarlarında e-posta adresi veya IP düz metin olarak saklanmaz. Ortak depoya ulaşılamazsa istekler bu yüzden reddedilmez; sınır o süre boyunca her örnekte ayrı uygulanır.

#### Scenario: İki örnek aynı sınırı paylaşır
- **WHEN** aynı kaynaktan gelen giriş denemeleri iki farklı uygulama örneğine dağılır ve toplamı sınırı aşar
- **THEN** sınırı aşan istekler hangi örneğe gelirse gelsin hız sınırı hatasıyla reddedilir

#### Scenario: Yeniden başlatma sayacı sıfırlamaz
- **WHEN** sınıra ulaşmış bir istemcinin penceresi dolmadan uygulama yeniden başlatılır
- **THEN** istemcinin sonraki istekleri pencere dolana kadar reddedilmeye devam eder

#### Scenario: Ortak depo erişilemez
- **WHEN** sayaç deposu o anda erişilemezdir
- **THEN** giriş ve kayıt istekleri hata vermeden işlenir ve sınır uygulama örneği başına uygulanmaya devam eder

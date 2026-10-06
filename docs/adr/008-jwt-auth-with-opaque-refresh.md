# [ADR-008] Kimlik doğrulama: JWT erişim token'ı + iptal edilebilir yenileme token'ı, tek hesap tablosu, zorunlu e-posta doğrulaması

**Tarih:** 2026-10-06
**Durum:** Accepted

---

## Context

Bahçeden'de her endpoint'in yetkilendirmesi, `web` oturum akışı, CSRF/CORS yapılandırması ve `account` tablosunun şekli tek bir karara bağlıydı: kimlik doğrulama nasıl yapılacak? Bu karar verilmeden ilk dikey dilim yazılamıyordu (`openspec/KALANLAR.md` B1).

Kararın alındığı andaki kısıtlar ve ihtiyaçlar:

- İki ayrı dağıtılabilir birim var ([ADR-001](001-spring-api-and-next-web.md)): `api/` otorite, `web/` istemci. `web` hem sunucu tarafında (Server Component) hem istemcide veri çekiyor.
- Ürün yol haritasında mobil uygulama ve ileride üçüncü parti istemci (satıcının kendi sistemi) ihtimali var; `web` dışında bir istemcinin aynı API'yi kullanabilmesi isteniyor.
- Çok satıcılı sistem: yetkilendirme satır seviyesinde ([ADR-007](007-query-level-ownership-scoping.md)), yani kimliğin güvenilir biçimde taşınması kritik.
- Pazaryerinde sahte hesap ve ulaşılamayan satıcı maliyetli: sipariş sonrası iletişim kurulamayan hesap doğrudan müşteri şikâyeti demek.
- Aynı kişi hem alıcı hem satıcı olabilir (üretici kendi de alışveriş yapar).

## Decision

### 1. Token biçimi: JWT erişim token'ı + opak yenileme token'ı

- **Erişim token'ı:** JWT, kısa ömürlü (15 dakika), `api` tarafında imzalanır ve doğrulanır. İmza anahtarı ortam değişkeninden okunur (`AGENTS.md` K10); kodda ve dokümanda yer almaz. Durumsuz doğrulama: her istek veritabanına gitmeden yetkilendirilebilir.
- **Yenileme token'ı:** JWT **değil**, opak rastgele dize (256 bit). Veritabanında **hash'li** saklanır; kullanıldığında döndürülür (rotation), çıkışta ve şüpheli kullanımda iptal edilir.

Bu, saf JWT'nin tek ciddi sorununu kapatır: "çıkış yaptım ama token hâlâ geçerli". Erişim token'ı 15 dakika yaşadığı için iptal penceresi kısa; yenileme yolu gerçekten iptal edilebilir.

### 2. Token'ların istemcide saklanması: `web` bir ince BFF'tir

- `web` giriş isteğini kendi Route Handler'ına alır, `api`'ye iletir ve dönen iki token'ı **`HttpOnly` + `Secure` + `SameSite=Lax` çerezlere** yazar (kendi origin'inde). Token'lar tarayıcı JavaScript'ine **hiç** verilmez.
- Sunucu tarafı render sırasında `web` çerezi okur ve `api`'ye `Authorization: Bearer <access>` başlığıyla gider.
- Erişim token'ı süresi dolduğunda `web` yenileme çerezi ile sessizce yeniler; yenileme de reddedilirse kullanıcı giriş ekranına yönlendirilir.
- **Mobil ve üçüncü parti istemciler** token'ları doğrudan `api` yanıt gövdesinden alır ve kendi güvenli deposunda tutar; `api` tarafında ikinci bir kimlik yolu açılmaz (`AGENTS.md` §15: iki kimlik yolu yasağı). Çerez yalnızca `web`'in kendi taşıma detayıdır.

### 3. Hesap modeli: tek tablo + roller

- Tek `accounts` tablosu; roller ayrı `account_roles` tablosunda (`ROLE_CUSTOMER`, `ROLE_SELLER`, `ROLE_ADMIN`). Aynı kişi hem alıcı hem satıcıdır: ikinci hesap açmaya gerek yok.
- Parolalar **BCrypt** ile hash'lenir (Spring Security varsayılanı). Parola hiçbir koşulda log'a, hata gövdesine veya test fixture'ına düz metin girmez.
- Satıcı olmaya özgü alanlar (vergi bilgisi, belge, onay durumu) `accounts` tablosunda değil, `sellers` tablosunda yaşar ve hesaba bağlanır; satıcı onboarding akışı ayrı bir karardır (`KALANLAR.md` A3).

### 4. E-posta doğrulaması: herkes için zorunlu

- Kayıt sonrası hesap `PENDING_VERIFICATION` durumunda açılır; **doğrulanmadan giriş yapılamaz** ve hiçbir işlem gerçekleştirilemez.
- Doğrulama token'ı tek kullanımlık, kısa ömürlü (24 saat) ve veritabanında **hash'li** saklanır. Parola sıfırlama token'ı aynı kurallara tabidir.
- Kayıt ve sıfırlama yanıtları, girilen e-postanın sistemde kayıtlı olup olmadığını **açığa çıkarmaz** (hesap keşfi engellenir).
- Giriş, kayıt, doğrulama tekrarı ve parola sıfırlama uçları hız sınırlıdır.

### 5. Sosyal giriş (Google vb.) MVP kapsamı dışındadır

İhtiyaç doğarsa yeni bir ADR ile gelir; bugün ikinci bir kimlik yolu açılmaz.

### 6. Kütüphane: Spring'in kendi JWT paketi

JWT imzalama ve doğrulama için `spring-boot-starter-oauth2-resource-server` kullanılır (Nimbus JOSE dahil gelir). Üçüncü parti bir JWT kütüphanesi (jjwt, java-jwt) **alınmaz**: Spring Security'nin `JwtEncoder` / `JwtDecoder` arayüzleri ihtiyacı karşılıyor ve bu paket Spring Boot sürümüyle hizalı bakım alıyor. Bu, [ADR-002](002-dependency-budget.md) bütçesini bir kalem genişletir; K7'nin dört sorusunun cevabı: platform kendi başına JWT üretemez; alternatifler jjwt (reddedildi: ekosistem dışı, ayrı sürüm takvimi) ve elle JOSE uygulaması (reddedildi: kriptografi elle yazılmaz); yük tek artifact ve Spring BOM ile yönetiliyor; katman `api/`.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Sunucu taraflı oturum + `HttpOnly` çerez (Spring Session) | En küçük güvenlik yüzeyi ve en basit çıkış davranışı; ancak mobil ve üçüncü parti istemci için ayrıca token tabanlı ikinci bir yol gerektirirdi. Tek API'nin tüm istemcilere aynı şekilde hizmet etmesi tercih edildi. |
| Saf JWT (yenileme token'ı da JWT) | Durumsuzluk cazip ama çıkış gerçek olmuyor ve ele geçirilmiş bir yenileme token'ı süresi boyunca kullanılabiliyor. Opak + hash'li + rotasyonlu yenileme bu riski kapatıyor; bedeli yenileme yolunda bir veritabanı okuması, ki o yol seyrek çağrılıyor. |
| Erişim token'ını `localStorage`'da tutmak | XSS ile doğrudan token sızması; tek bir üçüncü parti script hatası tüm oturumları açar. `HttpOnly` çerez bu sınıfı kapatıyor. |
| Token'ı bellekte (JS değişkeninde) tutmak | Sunucu tarafı render token'a erişemiyor, sayfa yenilemede oturum kayboluyor; Next.js App Router ile uyumsuz. |
| Dış kimlik sağlayıcı (Keycloak / Auth0) | Kullanıcı yönetimini dışarı verirdi ama [ADR-002](002-dependency-budget.md) bütçesine yeni bir servis/imaj veya dış bağımlılık eklerdi; tek sunuculu MVP için ağır. |
| E-posta doğrulamasını alıcı için ertelemek | Kayıt sürtünmesini azaltırdı; ancak pazaryerinde ulaşılamayan alıcı sipariş sonrası iletişim ve iade akışını kırıyor. Veri kalitesi dönüşüme tercih edildi. |

## Consequences

### Olumlu
- Tek API, tüm istemciler: mobil veya üçüncü parti istemci eklemek yeni bir kimlik yolu gerektirmiyor.
- Token tarayıcı JavaScript'ine hiç verilmediği için XSS ile oturum çalma yüzeyi kapalı.
- Çıkış gerçekten çıkış: yenileme token'ı iptal edilebiliyor, erişim penceresi 15 dakika.
- Doğrulanmış e-posta zorunlu: sipariş sonrası iletişim kurulabilir, sahte hesap maliyeti düşük.
- Yetkilendirme kimliği istek bağlamından okunuyor; [ADR-007](007-query-level-ownership-scoping.md) sahiplik kuralları güvenilir bir kimlik üzerine oturuyor.

### Olumsuz
- İki token, iki ömür, rotasyon ve iptal: saf oturum çerezinden daha fazla hareketli parça ve daha fazla test.
- `web` artık bir BFF katmanı taşıyor (Route Handler'lar + çerez yönetimi); sunum katmanı biraz kalınlaştı.
- `SameSite=Lax` çerez + durum değiştiren istekler CSRF koruması gerektiriyor; `web` kendi Route Handler'larına CSRF token'ı taşımak zorunda.
- E-posta doğrulaması zorunlu olduğu için kayıt akışı e-posta teslimine bağımlı: teslim edilemeyen e-posta doğrudan kayıp kullanıcı. Geliştirmede `mailpit`, üretimde teslim izleme gerekir.
- Hız sınırlama MVP'de uygulama içinde ve tek örnek varsayımıyla; yatay ölçeklemede paylaşımlı bir sayaç gerekecek (yeni ADR konusu).

### Nötr / Trade-off
- İmza anahtarının rotasyonu bir operasyon işidir: anahtar değiştiğinde tüm erişim token'ları geçersiz olur (en fazla 15 dakikalık kesinti penceresi). Yenileme token'ları etkilenmez.
- Doğrulanmamış hesaplar veritabanında birikir; temizleme işi bugün yok (ihtiyaç doğarsa ayrı karar).

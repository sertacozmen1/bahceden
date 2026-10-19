# [ADR-017] E-posta doğrulaması girişi engellemez; e-posta ikinci faktörü hesap bazlı bir anahtarla açılır

**Tarih:** 2026-10-06
**Durum:** Accepted
**Not (2026-10-19):** Bu kararın kabul ettiği borç (doğrulanmamış hesapla ticari eylem) iki eylemde kapatıldı: satıcı başvurusu [ADR-043](043-seller-application-requires-verified-email.md), sipariş [ADR-093](093-order-requires-verified-email.md). Giriş yine engellenmez.
**Supersedes:** [ADR-008](008-jwt-auth-with-opaque-refresh.md) §4'ün "doğrulanmadan giriş yapılamaz" kuralı. ADR-008'in diğer tüm kararları (JWT + opak yenileme, tek hesap tablosu, BCrypt, token kuralları, BFF) aynen yürürlüktedir.

---

## Context

[ADR-008](008-jwt-auth-with-opaque-refresh.md) §4 e-posta doğrulamasını **herkes için zorunlu** yaptı: hesap `PENDING_VERIFICATION` açılır ve doğrulanmadan giriş yapılamaz. Gerekçe veri kalitesiydi: pazaryerinde ulaşılamayan alıcı, sipariş sonrası iletişimi ve iade akışını kırar.

Uygulamaya geçildiğinde bu kuralın bugünkü maliyeti ortaya çıktı:

- **E-posta teslimi henüz kurulmadı ve sağlayıcı kararı açık** (`KALANLAR` B4/B5). Zorunlu doğrulama, kimlik diliminin tamamını teslim edilebilir e-postaya bağımlı kılıyor: `mailpit` görünür ama üretimde teslim edilmeyen bir e-posta doğrudan kilitlenmiş kullanıcı demek.
- **Parola sıfırlama bu dilimde kapsam dışı.** Zorunlu doğrulama + sıfırlamanın olmaması, doğrulama e-postası ulaşmayan kullanıcı için **hiçbir çıkış yolu** bırakmıyor.
- Geliştirme ve demo akışı her seferinde posta kutusundan token kopyalamayı gerektiriyor; bu, henüz gerçek kullanıcısı olmayan bir üründe karşılığı olmayan bir sürtünme.

Proje sahibi bu nedenle yönü değiştirdi: e-posta altyapısı **kurulsun** ama giriş yolunda **zorunlu olmasın**; e-postanın kimlik akışına girmesi hesap üzerindeki bir anahtara bağlı olsun ve o anahtar şimdilik kapalı kalsın.

## Decision

### 1. Kayıt hesabı doğrudan etkin açar

Kayıt sonrası hesap **`ACTIVE`** durumunda açılır. Doğrulama bağlantısı yine **gönderilir** ve tüketildiğinde `accounts.email_verified` `true` olur; ancak bu alan **girişi engellemez**. `PENDING_VERIFICATION` durumu kaldırılır: temsil edilmeyen bir durum enum'da tutulmaz.

`email_verified` bilgisi ileride davranış kapısı olarak kullanılabilir (ör. satıcı olmak, sipariş vermek doğrulanmış e-posta isteyebilir); bu dilimde yalnızca **kaydedilir ve gösterilir**, hiçbir yolu kapatmaz.

### 2. E-posta ikinci faktörü `accounts.two_factor_enabled` anahtarıyla açılır

`accounts.two_factor_enabled boolean not null default false`:

- **`false` (varsayılan ve bugün tek değer):** giriş tek adımdır; doğru parola erişim ve yenileme token'ını döndürür.
- **`true`:** giriş iki adımdır. Birinci adım token döndürmez; e-posta ile tek kullanımlık bir kod gönderilir ve ikinci adımda bu kod doğrulanınca token'lar verilir.

Anahtarı **değiştiren bir uç veya ekran yoktur**: ürün yüzeyinde kapalıdır ve yalnızca veritabanından (veya dev bağlamındaki seed'den) açılabilir. Bu yüzden pratikte "sabit kapalı"dır.

### 3. Anahtarın açık yolu da uygulanır ve testle sabitlenir

`true` yolu **yazılır, çalışır ve testi vardır.** Gerekçe iki kurala dayanır:

- **`AGENTS.md` §3:** hiçbir yerde okunmayan bir kolon "ileride lazım olur" demektir ve yasaktır. Anahtar ancak bir davranışı yönetiyorsa var olmayı hak eder.
- **`AGENTS.md` K6:** uygulanmamış bir dalı olan anahtar bir mayındır. Veritabanından `true` yapan biri girişi kırar; "yarım iş bırakma" kuralı tam olarak bunu yasaklar.

İkinci faktör kodu, doğrulama token'larıyla **aynı** altyapıyı kullanır (yüksek entropi, SHA-256 hash'li saklama, tek kullanımlık, kısa süre, hız sınırlı); ayrı bir mekanizma kurulmaz.

### 4. E-posta altyapısı tam kurulur

SMTP gönderimi, yapılandırma, kayıt ve testler bu dilimde tamamlanır. Gönderim hatası kullanıcı akışını **kesmez**: kayıt yine başarılı sayılır, hata `WARN` olarak loglanır. Böylece e-posta teslimi, kimliğin çalışması için ön koşul olmaktan çıkar.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| ADR-008'de kalmak (zorunlu doğrulama) | Kimlik dilimini teslim edilebilir e-postaya ve açık sağlayıcı kararına bağımlı kılıyor; parola sıfırlama da olmadığı için kullanıcıya çıkış yolu bırakmıyor. |
| E-posta altyapısını hiç kurmamak, doğrulamayı sonraya bırakmak | Altyapı sonraki her dilimin (sıfırlama, sipariş bildirimi, kargo bildirimi) ön koşulu; şimdi kurulması en ucuz an. Ayrıca `web` tarafında doğrulama ekranı zaten gerekiyor. |
| Anahtarı yalnızca kolon olarak eklemek, açık yolunu yazmamak | §3 (okunmayan kolon) ve K6 (uygulanmamış dal) ihlali; veritabanından açan biri girişi kırar. |
| Anahtarı global yapılandırmaya koymak (ortam değişkeni) | Hesap bazlı 2FA ileride kullanıcıya açılacak bir tercihtir; global anahtar o kapıyı kapatır ve "bu hesapta açık" senaryosunu test etmeyi zorlaştırır. |
| Doğrulamayı zorunlu tutup yalnızca dev ortamında atlamak | İki farklı kimlik davranışı demek: üretimde hiç denenmemiş bir yol kalır. `AGENTS.md` §15 iki alternatifi birlikte uygulamayı yasaklıyor. |

## Consequences

### Olumlu
- Kimlik dilimi e-posta teslimine ve sağlayıcı kararına bağımlı değil: `api` ayakta olduğu her ortamda kayıt ve giriş çalışıyor.
- Doğrulama e-postası ulaşmasa bile kullanıcı kilitlenmiyor; parola sıfırlamanın yokluğu (bilinçli non-goal) artık kritik bir boşluk değil.
- E-posta altyapısı yine kuruluyor ve **gerçekten kullanılıyor** (doğrulama bağlantısı + 2FA kodu), yani ölü kod değil.
- İkinci faktör, ürün hazır olduğunda yeni kod değil tek bir kolon güncellemesi gerektirecek.

### Olumsuz
- **ADR-008'in veri kalitesi gerekçesi bu dilimde karşılanmıyor:** doğrulanmamış e-postayla hesap açılıp sipariş verilebilir hale gelecek. Bu bilinçli bir borçtur; sipariş dilimi `email_verified` kapısını değerlendirmek zorundadır ve bu, `00-current-state.md` bilinen sınırlamalar tablosuna yazılır.
- Ürün yüzeyinde kapalı ama uygulanmış bir yol taşımak, test ve bakım yüzeyini büyütüyor (iki adımlı giriş akışı, ek uç, ek ekran).
- Kullanıcı tarafından açılamayan bir anahtar, "neden burada?" sorusunu doğuruyor; cevap yalnızca bu ADR'de duruyor.

### Nötr / Trade-off
- `PENDING_VERIFICATION` durumunun kaldırılması, hesap durum enum'unu bugün tek değere (`ACTIVE`) indiriyor. Enum yine enum olarak kalır ([ADR-010](010-product-status-as-enum.md) gerekçesi): askıya alma gibi ikinci bir durum geldiğinde şema göçü gerekmez.

# Design: add-password-reset

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2): D1 → ADR-033, D2 → ADR-034, D3 → ADR-035, D4 → ADR-036.
> Çerçeve [ADR-008](../../../../docs/adr/008-jwt-auth-with-opaque-refresh.md)'dir: "sıfırlama token'ları tek kullanımlık, kısa ömürlü ve hash'li saklanır; yanıtlar e-postanın kayıtlı olup olmadığını açığa çıkarmaz". Aşağıdaki kararlar bunun uygulama ayrıntılarını sabitler.

## Kararlar

### D1 · Sıfırlama token'ı: ayrı tablo, 1 saat, tek kullanımlık, yenisi eskileri kapatır

Token 256 bit rastgeledir; veritabanında yalnızca **SHA-256 hash'i** saklanır (doğrulama token'ı gibi, add-account-and-session D3). Ömrü **1 saattir**. Tüketildiğinde `used_at` yazılır.

Yeni bir sıfırlama talebi, hesabın kullanılmamış eski sıfırlama token'larını `used_at` ile kapatır. Böylece aynı anda yalnızca en son gönderilen bağlantı geçerlidir.

Token'lar `password_reset_tokens` adlı **ayrı bir tabloda** tutulur. Şekli `email_verification_tokens` ile aynıdır.

**Gerekçe:**
- Sıfırlama token'ı hesabı devralma yetkisi verir, doğrulama token'ı yalnızca bir bilgiyi işaretler. Risk farklı olduğu için ömür de farklıdır: 24 saat yerine 1 saat.
- Ayrı tablo, bir token türünün yanlışlıkla diğerinin yerine kabul edilmesini **şema düzeyinde** imkânsız kılar.
- Eski bağlantıları kapatmak, bir kullanıcının gelen kutusunda biriken birden fazla geçerli bağlantının saldırı yüzeyini kaldırır.

**Reddedilen alternatifler:**
- (a) `email_verification_tokens` tablosuna `purpose` kolonu eklemek: her sorgu amaca göre filtrelemek zorunda kalır, bir filtre unutulursa doğrulama token'ı parola değiştirir.
- (b) İmzalı, durumsuz token (JWT): tek kullanımlık yapılamaz ve iptal edilemez.
- (c) 24 saatlik ömür: gelen kutusunda uzun süre geçerli kalan devralma bağlantısı demektir.
- (d) Eski token'ları geçerli bırakmak: daha basit, ama yukarıdaki yüzeyi açık bırakır.

### D2 · Talep enumeration güvenlidir; kayıtsız adrese e-posta gönderilmez

`POST /api/v1/accounts/password-resets` her durumda **202** ve aynı gövdeyi (isteğin dilinde bilgi mesajı) döner:

| Durum | Davranış |
|---|---|
| Hesap var | Önceki kullanılmamış token'lar kapatılır, yeni token üretilir, bağlantı **hesabın dilinde** gönderilir ([ADR-032](../../../../docs/adr/032-account-locale-stored-and-refreshed-on-login.md)) |
| Hesap yok | Hiçbir şey yapılmaz, e-posta gönderilmez |

Hız sınırı iki anahtarla uygulanır:
- **İstemci başına** saatte 10 talep: tek kaynaktan adres taraması yavaşlar.
- **E-posta başına** saatte 3 talep: bir kurbanın gelen kutusu farklı adreslerden doldurulamaz.

**Gerekçe:**
- Kayıt ucundaki ilke burada da geçerli (add-account-and-session D4): yanıt hesabın varlığını söylemez.
- Kayıt ucundan farklı olarak, kayıtsız adrese "böyle bir hesap yok" e-postası **gönderilmez**. Böyle bir e-posta, sistemi rastgele adreslere istenmeyen posta gönderen bir araca çevirir. Sıfırlama talebinde bilgilendirilmesi gereken bir hesap sahibi de yoktur.

**Reddedilen alternatifler:**
- (a) Kayıtsız adreste 404 dönmek: hesap keşfi.
- (b) Kayıtsız adrese bilgilendirme e-postası göndermek: istenmeyen posta aracı.
- (c) Yalnızca istemci başına sınır: kurbanın gelen kutusu dağıtık kaynaklardan doldurulabilir.

### D3 · Başarılı sıfırlama tüm oturumları kapatır; otomatik giriş yok

Onay ucu tek bir transaction içinde şunları yapar:
1. Parola hash'ini değiştirir.
2. Token'ı tüketir.
3. Hesabın **bütün** yenileme token ailelerini iptal eder.
4. Açık giriş kodlarını (`login_challenges`) kapatır.

Ardından hesabın dilinde "parolanız değişti" bildirimi gönderilir. Yanıt **204**'tür; oturum açılmaz, kullanıcı `/login`'e yönlendirilir.

Yeni parolanın kuralları kayıttaki kurallarla aynıdır: 10–200 karakter, aynı doğrulama mesajları.

**Gerekçe:**
- Parola sıfırlamanın en sık sebebi parolanın ele geçirildiği şüphesidir. Eski parolayla açılmış oturumlar devam ederse sıfırlama saldırganı dışarı atmaz.
- Erişim token'ları en fazla 15 dakika daha geçerli kalır (ADR-008); yenilenemedikleri için oturum orada biter.
- Açık giriş kodları eski parolayla elde edilmiştir; geçerli kalırlarsa eski parola birkaç dakika daha işe yarar.
- Otomatik giriş yapılmaz, çünkü kimlik yolu tek kalmalıdır: ikinci faktörü açık bir hesap için sıfırlama bağlantısı ikinci adımı **atlatmamalıdır**. Giriş her zaman `POST /sessions` üzerinden geçer.
- "Parolanız değişti" bildirimi, kutusu ele geçirilmemiş ama bağlantısı başkası tarafından kullanılmış kullanıcıya haber verir.

**Reddedilen alternatifler:**
- (a) Yalnızca parolayı değiştirmek: ele geçirilmiş oturumlar sürer.
- (b) Sıfırlamadan sonra oturum açıp token dönmek: ikinci faktörü atlatır ve ikinci bir kimlik yolu açar (AGENTS.md §15 yasağı).
- (c) Bildirim göndermemek: kullanıcı, başkasının yaptığı bir sıfırlamadan habersiz kalır.

### D4 · Sıfırlama bağlantısını kullanmak e-postayı doğrular

Onay başarılı olduğunda hesap henüz doğrulanmamışsa `email_verified` işaretlenir.

**Gerekçe:**
- Sıfırlama bağlantısı yalnızca hesabın e-posta adresine gider. Bağlantıyı kullanan kişi o adresin gelen kutusuna erişmiştir; bu, doğrulama bağlantısının kanıtladığı şeyin aynısıdır.
- Aksi halde kullanıcıdan aynı şeyi ikinci bir e-postayla tekrar kanıtlaması istenir.

**Reddedilen alternatif:** doğrulama durumuna dokunmamak. Daha az etki, ama kullanıcıya gereksiz bir adım bırakır ve aynı kanıtın iki farklı sonucu olur.

## Mevcut ADR'lerden türeyen, yeni karar içermeyen detaylar

| Detay | Kaynak |
|---|---|
| Uç adları çoğul kaynak (`/password-resets`, `/confirmations`); durum değiştiren eylem alt kaynak | `AGENTS.md` §11.5 |
| Hata kodu `AUTH_TOKEN_INVALID` yeniden kullanılır; mesaj anahtarı `error.passwordResetTokenInvalid` | add-account-and-session D3, [ADR-031](../../../../docs/adr/031-api-messages-by-accept-language.md) |
| Hız sınırı bellek içi sabit pencere | [ADR-022](../../../../docs/adr/022-in-memory-fixed-window-rate-limiting.md) |
| BFF Route Handler + başlık tabanlı CSRF; token tarayıcıya verilmez | [ADR-023](../../../../docs/adr/023-web-bff-cookies-and-header-csrf.md) |
| E-posta gönderim hatası kullanıcı akışını kesmez, `WARN` loglanır | add-account-and-session D2, [ADR-017](../../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md) |
| Şema yalnızca Liquibase changeset'i ile, `--rollback` zorunlu | [ADR-016](../../../../docs/adr/016-liquibase-for-schema-migrations.md) |

## Risks / Trade-offs

| Konu | Değerlendirme |
|---|---|
| Zamanlama farkıyla hesap keşfi | Kayıt ucundaki yüzeyle aynı; hız sınırı daraltır. Kalıcı çözüm asenkron gönderim, bugün §3 dışı |
| E-posta kutusu ele geçirilirse hesap da ele geçirilir | E-postayla kurtarmanın doğası; bildirim ve oturum kapatma zararı sınırlar. İkinci faktör açık hesapta saldırgan yine giriş kodunu e-postadan alır; ek koruma sağlamaz |
| Talep sırasında e-posta gönderilemezse kullanıcı bağlantıyı alamaz | Yanıt yine 202'dir (enumeration). Kullanıcı tekrar dener; hız sınırı 3/saat |
| Bütün oturumların kapanması kullanıcının diğer cihazlarını da kapatır | Bilinçli: amaç tam olarak bu |

## Migration Plan

1. Liquibase `006-create-password-reset-tokens.sql`: tablo + hash tekil kısıtı + `account_id` yabancı anahtarı (`on delete cascade`) + `expires_at` ve `account_id` indeksleri. `--rollback` tabloyu düşürür.
2. Dev seed değişmez: mevcut iki sahte hesap sıfırlama akışını elle denemek için yeterli (mailpit).
3. Mevcut tablolar etkilenmez; uygulanmış changeset'lere dokunulmaz.

## Open Questions

Bu dilimi bloke etmeyen sorular:
- Oturum açıkken parola değiştirme ucu ne zaman gelecek? Bu change'in parola kuralları ve "tüm oturumları kapat" yardımcısı ona doğrudan taşınır.
- Süresi dolmuş token satırlarının temizliği (L15) bu tabloyu da kapsamalı.

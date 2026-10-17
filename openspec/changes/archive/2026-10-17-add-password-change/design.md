# Design: add-password-change

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2): D1 → ADR-087, D2 → ADR-088, D3 → ADR-089, D4 → ADR-090.
> Çerçeve [ADR-008](../../../../docs/adr/008-jwt-auth-with-opaque-refresh.md) (kimlik) ve [ADR-035](../../../../docs/adr/035-password-reset-signs-out-everywhere-no-auto-login.md)'tir (parola değişince bütün oturumlar kapanır, otomatik giriş yok). Bu change ADR-035'in kuralını oturumlu yola taşır; hiçbir ADR'ye aykırı değildir.

## Kararlar

### D1 · Uç: `POST /api/v1/accounts/me/password-changes`, mevcut ve yeni parola, 204

İstek gövdesi `{ "currentPassword": "…", "newPassword": "…" }`. Uç oturum ister (`Authorization: Bearer`); hesap token'dan gelir, gövdeden değil. Başarı **204**'tür, gövde yoktur.

Doğrulama sırası:

1. Bean Validation: iki alan da dolu; `newPassword` kayıt kurallarıyla aynı (10–200 karakter). Hata → **400** `VALIDATION_FAILED`.
2. Mevcut parola hash ile karşılaştırılır (D2).
3. `newPassword` mevcut parolayla aynıysa **400** `VALIDATION_FAILED`, `newPassword` alanında hata. Aynı parolayı "değiştirmek" bütün oturumları kapatıp hiçbir şey kazandırmaz; kullanıcı büyük ihtimalle yanlış alana yazmıştır.

**Gerekçe:**
- Ad, mevcut açık komut uçlarıyla aynı düzende: `me/verification-requests`, `password-resets/confirmations`. Parola okunabilir bir kaynak değildir; değiştirme bir eylemdir.
- Mevcut parolayı istemek, kilitlenmemiş bir cihazda açık kalan oturumla parolanın devralınmasını engeller.

**Reddedilen alternatifler:**
- (a) `PATCH /accounts/me/password` veya `PUT`: okunamayan bir "parola" kaynağını güncelliyormuş gibi gösterir; projede eylem uçları komut adıyla yazılıyor.
- (b) Mevcut parolayı istememek: oturumu ele geçiren hesabı da kalıcı olarak ele geçirir.
- (c) Aynı parolaya izin vermek: zararsız ama kullanıcıya hiçbir şey kazandırmadan bütün cihazlarından çıkarır.

### D2 · Yanlış mevcut parola alan hatasıdır; hesap başına hız sınırı

Mevcut parola yanlışsa **400** `VALIDATION_FAILED` döner ve hata `fieldErrors` içinde `currentPassword` alanına bağlanır. Mesaj anahtarı `validation.currentPassword.invalid`.

Uç hesap başına **15 dakikada 5 istek** ile sınırlanır (`RateLimitPolicy.PASSWORD_CHANGE`). Sınır, mevcut parola denetiminden **önce** uygulanır; başarılı istekler de sayılır. Aşılırsa **429** `RATE_LIMITED`.

**Gerekçe:**
- Kullanıcı açısından bu bir form hatasıdır: hangi alanın yanlış olduğunu bilmesi gerekir, oturumu ise geçerlidir. Formlar `fieldErrors`'ı zaten ilgili alana bağlıyor (§12.3).
- Oturumlu uçlarda 401 "oturum yok veya geçersiz" demektir (`AUTH_REQUIRED`); `web` de oturum durumunu 401 ile okur (`features/account/api.ts`). Yanlış parola için 401 dönmek, oturumu geçerli olan kullanıcının hatasını oturum hatasıyla karıştırır; form da hatayı alana değil genel mesaja bağlar.
- Anahtar hesaptır, istemci adresi değil: saldırgan çalınmış oturumu farklı adreslerden kullanabilir. Aynı desen `VERIFICATION_REQUEST`'te de var.
- 15 dakikada 5 deneme, kendi parolasını hatırlamaya çalışan kişiyi rahatsız etmez; kaba kuvveti anlamsız kılar.

**Reddedilen alternatifler:**
- (a) **401** `AUTH_INVALID_CREDENTIALS`: oturum hatasıyla karışır (yukarıda).
- (b) Yeni bir hata kodu (örn. **422** `CURRENT_PASSWORD_INVALID`): alan hatası olan bir durum için ikinci bir eşleme yolu açar; form yine alanı bulmak zorunda kalır.
- (c) İstemci adresine göre sınır: tek bir oturumla dağıtık deneme yapılabilir.
- (d) Yanlış denemelerde hesabı kilitlemek: kilit, hesabı başkasının kapatabileceği bir düğmeye dönüştürür; hız sınırı yeterli.

### D3 · Başarılı değişiklik bütün oturumları kapatır (mevcut dahil); oturum açılmaz; e-posta doğrulanmış sayılmaz

Tek transaction içinde:

1. Parola hash'i değişir (`Account.changePassword`, sıfırlamadakiyle aynı yöntem).
2. Hesabın **bütün** yenileme token aileleri iptal edilir; isteği yapan oturum da dahil (`RefreshTokenRepository.revokeAllForAccount`).
3. Hesabın açık giriş kodları kapanır (`LoginChallengeRepository.closeOpenChallenges`).

Ardından hesabın dilinde mevcut "parolanız değişti" e-postası gönderilir (`mail.passwordChanged.*`; içinde sıfırlama bağlantısı istenecek adres var). Gönderim başarısız olursa değişiklik yine başarılıdır, hata yalnızca loglanır ([ADR-019](../../../../docs/adr/019-email-via-smtp-provider-agnostic.md)).

Yanıt yeni token **içermez**. `email_verified` **değişmez**.

**Gerekçe:**
- Proje sahibinin kararı (2026-10-08): mevcut oturum dahil bütün oturumlar kapanır. Parola değiştirmenin en sık sebebi sızıntı şüphesidir; o anda hangi oturumun güvenilir olduğu bilinemez.
- ADR-035 ile aynı kural, aynı sorgular: parolanın değiştiği iki yol aynı sonucu verir; kullanıcının ve destek ekibinin tek bir kuralı bilmesi yeter.
- Yeni token dönmek, `POST /sessions` dışında ikinci bir oturum açma yolu olur (`AGENTS.md` §15).
- Sıfırlama e-postayı doğrular, çünkü bağlantıyı kullanan gelen kutusuna erişmiştir ([ADR-036](../../../../docs/adr/036-password-reset-marks-email-verified.md)). Parolayı bilmek bunu kanıtlamaz.

**Reddedilen alternatifler:**
- (a) Mevcut oturumu açık tutup diğerlerini kapatmak: daha az sürtünme; ancak proje sahibi reddetti ve oturumun kendisi çalınmışsa saldırgan içeride kalır. Ayrıca yenileme token ailesini istekle eşlemek için `web`'in yenileme token'ını da göndermesi gerekirdi.
- (b) Değişiklik sonrası yeni token çifti dönmek: ikinci kimlik yolu, ikinci faktörü de atlar.
- (c) Oturumlara dokunmamak: sızıntı şüphesinde değiştirmenin anlamını yok eder.
- (d) `email_verified` işaretlemek: gerçeğe dayanmayan bir doğrulama bilgisi üretir.

### D4 · `web`: ayrı sayfa, BFF başarıda çerezleri siler, giriş ekranına yönlendirir

- Sayfa `/account/password`, Hesabım kapısının (`(account)/account/layout.tsx`) altında: oturumsuz istek girişe yönlenir. Dönüş adresini kapı belirler ve `/account`'tur; siparişler ve adresler sayfaları da bugün böyle çalışır (kapı düzende olduğu için alt sayfanın yolunu bilmez). Kullanıcı Hesabım'dan tek tıkla sayfaya geri gelir.
- Hesabım'da, alışveriş bağlantılarından sonra ve satıcılık bölümünden önce "Güvenlik" başlığıyla tek bir "Parolayı değiştir" bağlantısı.
- Form iki alan içerir: mevcut parola (`autocomplete="current-password"`) ve yeni parola (`autocomplete="new-password"`, kural ipucu). Yeni parolayı tekrar yazdırma alanı **yok**: kayıt ve sıfırlama formlarında da yok, aynı kalıp sürer.
- Formun üstünde, değişikliğin bütün cihazlardan çıkaracağını söyleyen kısa bir not durur (sonucu önceden söyle, §12.5).
- BFF Route Handler `/api/auth/password-change`: CSRF başlığı (`handleBffMutation`), erişim token'ı çerezden. `api` 204 dönerse **oturum çerezleri silinir** ve 204 döner. Hata durumunda çerezlere dokunulmaz, hata gövdesi aynen iletilir.
- Başarıda form `/login?reset=1` adresine gider; mevcut "Parolanız değişti ve açık oturumlarınız kapatıldı. Yeni parolanızla giriş yapın." mesajı burada görünür.

**Gerekçe:**
- Hesabım sayfası zaten kimlik, doğrulama, alışveriş ve satıcılık bölümlerini taşıyor; bir form daha eklemek tek odak kuralını bozar (Miller). Ayrı sayfa aynı zamanda kendi yükleniyor ve hata durumlarına sahip olur.
- Çerezleri BFF'de, başarıdan hemen sonra silmek, tarayıcıda iptal edilmiş bir yenileme token'ının kalmasını önler. Kalırsa ilk yenileme denemesi "yeniden kullanım" olarak işlenir ve kullanıcı açıklamasız bir oturum sonuyla karşılaşır.
- Giriş ekranının mesajı ve `reset=1` parametresi sıfırlamadan beri var; ikinci bir mesaj yazmak gerekmiyor.

**Reddedilen alternatifler:**
- (a) Hesabım içinde katlanır form: sayfanın yükünü artırır ve hata durumunu tüm sayfayla paylaşır.
- (b) Diyalog: parola yöneticileri diyalog içindeki formları daha zayıf tanıyor; geri tuşu ve doğrudan bağlantı da çalışmaz.
- (c) Çerezleri istemcide `SignOutButton` akışıyla silmek: ikinci bir istek ve iptal edilmiş token için gereksiz bir `logout` çağrısı.
- (d) "Yeni parola tekrar" alanı: projede başka parola formunda yok; görünür kural ipucu ve sunucu doğrulaması yeterli.

## Yetkilendirme ve kişisel veri

- Uç yalnızca oturum sahibinin kendi hesabına etki eder; hesap kimliği JWT'den gelir. Başka hesabı hedefleyen parametre yoktur, dolayısıyla sahiplik ihlali yüzeyi de yoktur.
- `SecurityConfig`'te uç açıkça `authenticated()` işaretlenir; oturumsuz istek **401**.
- Parolalar loglanmaz, istek gövdesi hata yanıtına yansımaz. Log satırı yalnızca hesap kimliğini ve iptal edilen token sayısını yazar.
- Yeni kişisel veri alanı ve yeni dış veri akışı yoktur.

## Risks / Trade-offs

- **Erişim token'ları en fazla 15 dakika daha geçerli** (ADR-008). Diğer cihazlardaki açık sekmeler bu süre içinde istek yapabilir, sonra yenileyemez. ADR-035 ile aynı bedel.
- **Kullanıcı bütün cihazlarında yeniden giriş yapar.** Bilinçli karar (D3).
- **Hız sınırı bellekte ve tek örnek için** ([ADR-022](../../../../docs/adr/022-in-memory-fixed-window-rate-limiting.md)): yeniden başlatma sayacı sıfırlar. Mevcut bütün politikalarla aynı sınırlama.

## Migration Plan

Şema değişikliği yok. Uç ekleyicidir; geri almak için uç ve sayfa kaldırılır, veri dönüşümü gerekmez.

## Open Questions

Yok. Oturum kapsamı kararı 2026-10-08'de verildi.

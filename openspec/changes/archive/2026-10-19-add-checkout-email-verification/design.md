# Design: add-checkout-email-verification

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2): D1 → ADR-093, D2 → ADR-094, D3 → ADR-095.
> Çerçeve [ADR-017](../../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md) (doğrulama girişi engellemez, borç L11) ve emsal [ADR-043](../../../../docs/adr/043-seller-application-requires-verified-email.md)'tür (satıcı başvurusunda kapı). ADR-017'ye aykırı değildir: giriş engellenmez, kapı bir eyleme konur. ADR-017 ve ADR-043'e bu ADR'lere işaret eden not düşülür.

## Kararlar

### D1 · Kapı sipariş oluşturmada: `422 ORDER_EMAIL_NOT_VERIFIED`; tekrar anahtarı kapıdan önce

`CheckoutService.placeOrder` sırası:

1. `Idempotency-Key` biçimi (mevcut).
2. Aynı anahtarla önceden oluşmuş sipariş varsa o döner (mevcut). Kapıdan **önce**: sipariş zaten oluşmuştur, kapı onu geri alamaz.
3. **Yeni:** hesabın `email_verified` değeri `false` ise `422 ORDER_EMAIL_NOT_VERIFIED`, mesaj `error.orderEmailNotVerified`. Hesap `AccountRepository`'den taze okunur (ADR-024 deseni); JWT iddiasına güvenilmez.
4. Adres, sepet ve ödeme (mevcut).

Kapı yalnızca bu uçtadır. Sepet, adres defteri, ödemenin tamamlanması ve sipariş görüntüleme kapısızdır.

**Gerekçe:**
- Para ödenen ve e-postayla izlenen ilk adım sipariş oluşturmadır. Sepet ve adres geri alınabilir hazırlıklardır; onları kapamak yalnızca sürtünme ekler.
- Ödeme tamamlamayı kapamak, kapıdan önce açılmış siparişleri ödenemez bırakırdı; para sağlayıcıda çekilmiş olabilir.
- Kod, alanı adlandırır: başvuru kapısı `SELLER_EMAIL_NOT_VERIFIED`'dı. `web` iki durumu ayrı metinle anlatır.
- Doğrulama durumu JWT'de yok; `GET /accounts/me` de taze okur (ADR-024). Kapının aynı kaynaktan beslenmesi, doğruladıktan hemen sonra token yenilenmeden sipariş verilebilmesini sağlar.

**Reddedilen alternatifler:**
- (a) Girişte kapı: ADR-017'ye aykırı; alıcı sepetini ve siparişlerini göremez olur.
- (b) Sepete eklemede kapı: vitrin akışını erken keser; satın alma niyetini kaybettirir.
- (c) Ödeme tamamlamada kapı: yukarıdaki para riski.
- (d) `SELLER_EMAIL_NOT_VERIFIED`'ı yeniden kullanmak: ad yanlış alanı söyler. Genel bir `EMAIL_NOT_VERIFIED`'a geçmek mevcut kodu değiştirmeyi gerektirir; sözleşmede kırıcı değişiklik (§9).
- (e) **403**: yetki eksikliği değil, kullanıcının giderebileceği bir iş kuralı; projede bu durum 422 ile modelleniyor.

### D2 · `web`: ödeme sayfası doğrulanmamış alıcıya form yerine doğrulama paneli gösterir

- `/checkout` sayfası oturumdan gelen hesap bilgisini (`account.emailVerified`, `requireSignedIn`) kullanır. Doğrulanmamışsa sağ kartta `CheckoutForm` yerine:
  - başlık "Önce e-postanızı doğrulayın";
  - neden gerektiğini söyleyen kısa metin (sipariş, kargo ve teslim haberleri bu adrese gider) ve spam klasörüne bakma önerisi;
  - mevcut `ResendVerificationButton`.
- Sipariş özeti, toplamlar ve cayma bilgisi aynı kalır; alıcı ne ödeyeceğini görmeye devam eder.
- Sepet sayfası ve "Siparişe geç" bağlantısı değişmez. Kapı tek yerde anlatılır.
- Sunucu yine de 422 dönerse (`CheckoutForm` açıkken durum değişmiş olamaz ama otorite `api`'dir) mevcut hata eşlemesi mesajı formun üstünde gösterir; ek kod gerekmez.

**Gerekçe:**
- Satıcı başvurusu sayfası aynı deseni kullanıyor: form yerine doğrulama yönlendirmesi ve mevcut düğme (ADR-043). Kullanıcı tek bir kalıp öğrenir.
- Özeti göstermeye devam etmek, alıcının neyi tamamlamak üzere olduğunu unutturmaz.
- Düğmeyi sepete de koymak kapıyı iki yerde anlatır ve sepet ekranını kalabalıklaştırır.

**Reddedilen alternatifler:**
- (a) Formu gösterip "Ödemeye geç"te sunucu hatasını göstermek: alıcı adres seçip düğmeye bastıktan sonra öğrenir; boşa iş.
- (b) Sepette "Siparişe geç"i kapatmak: nedenini anlatacak yer dar, düğme devre dışı kalır; erişilebilirlik ve anlaşılırlık zayıf.
- (c) Ayrı bir `/checkout/verify` sayfası: yeni rota ve yönlendirme; panel yeterli.

### D3 · Geliştirme verisi: `alici@ornek.test` doğrulanmış olur, yeni dev changeset'iyle

- Yeni changeset `015-dev-seed-verified-buyer.sql` (`contextFilter:dev`): `update accounts set email_verified = true, updated_at = now() where email = 'alici@ornek.test' and email_verified = false`. İdempotent.
- `--rollback` hesabı yeniden doğrulanmamış yapar.
- `docs/how-to/yerel-gelistirme.md` dev hesapları tablosu güncellenir. Kapıyı denemek için yeni bir hesap açılır; doğrulama bağlantısı mailpit'tedir.

**Gerekçe:**
- Belgelenmiş bütün geliştirme akışları (sipariş, kargo, ayıplı ürün) bu hesapla başlıyor. Doğrulanmamış kalırsa her biri bir mailpit adımı daha ister.
- Uygulanmış `004` changeset'i düzenlenemez (K8); düzeltme yeni bir changeset'tir.
- Kapıyı göstermek için yeni hesap açmak gerçek kullanıcının yolunun aynısıdır.

**Reddedilen alternatifler:**
- (a) Alıcıyı doğrulanmamış bırakmak: geliştirme akışları uzar.
- (b) Doğrulanmamış ikinci bir dev hesabı eklemek: kayıt formu zaten bunu sağlıyor; seed büyür.
- (c) `004`'ü düzenlemek: checksum hatası, K8 ihlali.

## Yetkilendirme ve kişisel veri

- Kapı oturum sahibinin kendi hesabını okur; başka hesaba bakmaz.
- Yeni kişisel veri alanı veya veri akışı yok. Log'a hesap kimliği yazılır, e-posta yazılmaz.

## Risks / Trade-offs

- **İlk siparişte bir adım daha:** bedeli; karşılığında sipariş haberleri teslim edilebilir bir adrese gider.
- **Doğrulamadan sonra dönüş yolu yok:** doğrulama sayfası alıcıyı ödemeye geri götürmez; alıcı ödeme sekmesini yeniler. Non-goal olarak not edildi.
- **Var olan doğrulanmamış hesaplar:** canlıda henüz kullanıcı yok; geliştirmede yalnızca seed hesabı etkilenir (D3).

## Migration Plan

Şema değişmez. Dev changeset'i yalnızca `dev` bağlamında çalışır; testler ve üretim etkilenmez (`contextFilter` izolasyonu testle sabit). Geri almak için kapı satırı ve panel kaldırılır, dev changeset'i rollback ile geri alınır.

## Open Questions

Yok. Karar 2026-10-17'de verildi.

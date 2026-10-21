# Design: add-account-locale-update

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2): D1 → ADR-098, D2 → ADR-099.
> Çerçeve [ADR-032](../../../../docs/adr/032-account-locale-stored-and-refreshed-on-login.md)'dir: hesabın dili saklanır, kayıtta yazılır, girişte güncellenir, e-postalar o dilde gider. Bu change ADR-032'yi çiğnemez. ADR-032'nin "ekran gelince ayrı karar" diye ertelediği ucu ekler. ADR-032'ye bu ADR'lere işaret eden bir not düşülür. Dil seçiminin kendisi [ADR-030](../../../../docs/adr/030-locale-from-cookie-then-header.md)'dur ve değişmez.

## Kararlar

### D1 · `api`: oturum sahibinin dilini açık değerle değiştiren uç

- **Uç:** `PUT /api/v1/accounts/me/locale`, kimlik doğrulamalı.
  - Gövde `AccountLocaleUpdate { locale: SupportedLocale }`; `@NotNull`, değerler `TR` / `EN`.
  - Yanıt `204`, gövdesiz.
- **Kapsam:** yalnızca erişim token'ının sahibi olan hesap değişir. Uçta hesap kimliği yoktur; başkasının hesabı adreslenemez ([ADR-007](../../../../docs/adr/007-query-level-ownership-scoping.md) ile aynı ilke).
- **İş:**
  - `AccountService.changeLocale(accountId, locale)` mevcut `Account.useLocale`'i çağırır.
  - Dil aynıysa satır güncellenmez (`updated_at` değişmez). Uç idempotenttir.
- **Hatalar:**
  - token yok veya geçersiz → `401`;
  - gövde yok, alan yok veya değer tanınmıyor → `400`, mevcut hata gövdesiyle.
  - Hesap token'dan sonra silinmişse `401` (`AUTH_REQUIRED`), mevcut `/accounts/me` uçlarıyla aynı (`AccountService.currentAccount`).
- **Hız sınırı yok:** uç ucuzdur, kimlik doğrulamalıdır, e-posta veya dış çağrı üretmez. Sepet ve adres uçlarıyla aynı sınıftadır.
- **Yanıt mesajları:** isteğin dilindedir ([ADR-031](../../../../docs/adr/031-api-messages-by-accept-language.md)). Hesabın yeni dili yalnızca e-postaları etkiler.

**Gerekçe:**
- Değer gövdede açıkça verilir. Uç `Accept-Language`'den okusaydı, dilin taşıyıcısı ile değiştirilecek değer aynı başlık olurdu. İstemcinin başlık düzeni yanlış dili sessizce yazabilirdi.
- `PUT`: kaynağın (hesabın dili) tamamı yazılır ve aynı istek tekrarlanabilir.
- `SupportedLocale` zaten tek dil listesidir. Sözleşmeye enum olarak çıkması, `web`'in geçersiz bir değer göndermesini tip seviyesinde engeller.

**Reddedilen alternatifler:**
- (a) Gövdesiz uç, dil `Accept-Language`'den: yukarıdaki belirsizlik.
- (b) Genel bir `PATCH /accounts/me` "profil" ucu: bugün değiştirilebilecek tek alan dil. Genel uç, alan başına yetki ve doğrulama sorusunu bugünden açar (§3).
- (c) Dili her kimlik doğrulamalı istekte `Accept-Language`'den güncellemek: her okuma bir yazmaya dönüşür. Sepet ekranını açmak hesabı değiştirmemeli.
- (d) Enum yerine serbest metin (`"en"`) ve sunucuda doğrulama: tanınmayan değer varsayılana düşerdi (`SupportedLocale.fromTag`). Yanlış değer hata vermez, hesap sessizce Türkçeye döner.

### D2 · `web`: dil menüsü çerezi yazar, hesabı en iyi çabayla eşitler

- **Sıra:** `LocaleMenu.choose` şu adımları izler:
  1. çerezi yazar (`saveLocale`, bugünkü gibi);
  2. BFF'i çağırır: `PUT /api/account/locale`, gövde `{"locale": "en"}`;
  3. sonuç ne olursa olsun `router.refresh()` çağırır.
- **BFF (`app/api/account/locale/route.ts`):**
  - Köken denetimi ve gövde okuma `handleBffMutation` ile, diğer BFF uçlarıyla aynıdır.
  - Gövdedeki değer `parseLocale` ile daraltılır; tanınmazsa `400`.
  - Erişim çerezi yoksa `api`'ye gitmez → `204`.
  - Çerez varsa `api`'ye `TR` / `EN` ile gider.
  - `api` `401` dönerse `204` döner: erişim token'ı geçmiştir ve hesabın dili bir sonraki girişte yetişir (ADR-032).
  - Diğer `api` hataları mevcut desende aynen iletilir.
- **İstemci:** eşitleme hatası kullanıcıya gösterilmez ve dil değişikliğini geri almaz. Ekrandaki dil cihazın tercihidir; hesabın dili bir sonraki girişte yine yetişir.
- **Oturum durumu istemcide bilinmez:** menü oturum açık mı diye bakmaz. Karar BFF'te, çerezle verilir. Menü bileşeni oturum bilgisi almaz, yerleşimi değişmez.

**Gerekçe:**
- Çerez önce yazılır. Böylece dil değişikliği ağ hatasında da çalışır ve sayfa yenilemesi, aynı istekte yeni dille gelir (ADR-030).
- Token tarayıcıya hiç verilmez; `api` çağrısı yalnızca BFF'ten yapılır ([ADR-008](../../../../docs/adr/008-jwt-auth-with-opaque-refresh.md)).
- `401` için yenileme denemesi yapılmaz. Sayfalar oturumu kendi akışıyla yeniler (`SessionAutoRefresh`). Dil eşitlemesi için yenileme token'ının yolunu (`/api/auth`) genişletmek gerekmez.

**Reddedilen alternatifler:**
- (a) BFF'in çerezi de sunucuda yazması ve istemcinin yalnızca BFF'i çağırması: ağ hatasında dil hiç değişmez. Çerez yazımı iki yere dağılır.
- (b) BFF'i `/api/auth` altına koyup `401`'de token yenilemek: yenileme çerezi daha fazla yola gider ve dil seçimi bir oturum yenileme akışına dönüşür. Kazanç, girişe kadar süren küçük bir gecikmeyi kapatmaktan ibaret.
- (c) Menüye `signedIn` bilgisi geçirip anonim ziyaretçide BFF'i hiç çağırmamak: başlık düzeni oturum bilgisini menüye taşımak zorunda kalır. Kazanç küçük bir isteği atlamak.
- (d) Eşitleme hatasında kullanıcıya uyarı: kullanıcının yapabileceği bir şey yok. Dil yine değişti; e-postalar en geç sonraki girişte yetişir.

## Yetkilendirme ve kişisel veri

- Yeni uç kimlik doğrulamalıdır; `SecurityConfig`'te açıkça `authenticated()` işaretlenir (varsayılan deny).
- Başkasının hesabı adreslenemez: uçta kimlik yok, hesap token'dan gelir.
- Dil tercihi log'a yazılmaz.

## Test etkisi

- **`api` (`AccountLocaleTest`, `critical`):**
  - mutlu yol: dil `EN` olur, ardından istenen doğrulama e-postası İngilizce gider;
  - aynı dil: `updated_at` değişmez;
  - geçersiz değer ve eksik alan `400`;
  - token yok `401`;
  - başka hesabın dili değişmez.
- **`OpenApiContractTest`:** sözleşme yeniden üretilir.
- **`web`:**
  - Route Handler testi: oturum yok → `api` çağrılmaz, `204`; oturum var → `api`'ye `EN` gider, `204`; `api` `401` → `204`; tanınmayan değer → `400`; siteler arası → `403`.
  - `LocaleMenu` testi: seçimde BFF çağrılır ve sayfa yenilenir; BFF hata verse de yenilenir.
- **Kırılması beklenen mevcut test yok.** `LocaleMenu.test.tsx` `fetch` mock'u alır.

## Risks / Trade-offs

- **Geçmiş erişim token'ı:** kullanıcı uzun süre boşta kaldıktan sonra ilk iş dil değiştirirse eşitleme `401` ile atlanır. Hesabın dili sonraki girişte yetişir; bugünkü davranıştan kötü değil.
- **İki cihaz:** hesabın dili son seçimi veya son girişi izler (ADR-032 "en son kullanılan dil").

## Migration Plan

Şema değişmez. Ekleyici sözleşme değişikliği. Geri alma: uç ve BFF kaldırılır, menü eski davranışa döner.

## Open Questions

Yok.

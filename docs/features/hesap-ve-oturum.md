# Hesap ve Oturum

**Durum:** Yayında
**İlk sürüm:** 2026-10-07
**Son değişiklik:** 2026-10-09

| | |
|---|---|
| **Kararlar** | [ADR-008](../adr/008-jwt-auth-with-opaque-refresh.md), [ADR-017](../adr/017-email-verification-non-blocking-and-2fa-switch.md), [ADR-018](../adr/018-refresh-token-rotation-with-family-revocation.md) … [ADR-026](../adr/026-verification-link-resend-requires-session.md), parola sıfırlama [ADR-033](../adr/033-password-reset-token-separate-table-one-hour.md) … [ADR-036](../adr/036-password-reset-marks-email-verified.md), oturumun sürmesi [ADR-069](../adr/069-refresh-token-reuse-grace-window.md), [ADR-070](../adr/070-silent-session-refresh-on-any-page.md) |
| **Spec (capability)** | `openspec/specs/account-identity` |
| **Change'ler** | `openspec/changes/archive/2026-10-07-add-account-and-session`, `openspec/changes/archive/2026-10-07-add-password-reset`, `openspec/changes/archive/2026-10-09-change-session-persistence` |
| **Rehberler** | [Korunan uç ekleme](../how-to/korunan-uc-ekleme.md), [Yerel geliştirme](../how-to/yerel-gelistirme.md) |
| **Referans** | [Domain modeli](../reference/01-domain-model.md), [API referansı](../reference/02-api-reference.md) |
| **Kod** | `api/src/main/java/com/bahceden/account/`, `api/.../common/security/`, `api/.../common/ratelimit/`, `web/src/app/(account)/`, `web/src/app/api/auth/`, `web/src/features/account/` |

---

## Ne yapar

Kullanıcı e-posta adresi ve parolayla hesap açar ve hesabını hemen kullanır; e-posta adresini doğrulamak girişin koşulu değildir, yalnızca hesapta bir bilgi olarak durur. Doğrulama bağlantısı e-postayla gelir; süresi dolarsa veya ulaşmazsa "Hesabım" sayfasından yenisi istenir. Giriş yapan kullanıcı üst başlıkta maskeli e-postasını ve çıkış düğmesini görür; oturum arka planda yenilenir; tarayıcıyı kapatıp açan kullanıcı hangi sayfayı açarsa açsın oturumunu açık bulur. Çıkış gerçekten oturumu kapatır. Hesabında ikinci faktör açık olan kullanıcı girişte e-postasına gelen 6 haneli kodu da girer. Bu anahtar bugün yalnızca veritabanından açılabiliyor. Parolasını unutan kullanıcı giriş ekranındaki "Parolamı unuttum" bağlantısıyla e-postasına bir saatlik sıfırlama bağlantısı ister; yeni parolayı belirleyince bütün cihazlardaki oturumları kapanır ve yeni parolasıyla giriş yapar.

## Bilinçli sınırlar

- **Oturum açıkken parola değiştirme yok.** Sıfırlama altyapısı (parola kuralları, bütün oturumları kapatma) ona doğrudan taşınır.
- **İkinci faktörü kullanıcı açamaz** ([ADR-017](../adr/017-email-verification-non-blocking-and-2fa-switch.md)); anahtar yalnızca veritabanından veya dev seed'inden açılır.
- **Doğrulanmamış e-postayla hesap açılabilir.** Bu kabul edilmiş bir borç. Kapı ilk olarak satıcı başvurusuna kondu ([ADR-043](../adr/043-seller-application-requires-verified-email.md)); sipariş dilimi kendi kapısını değerlendirecek.
- **Satıcılık ayrı feature'dır:** başvuru ve onay [Satıcı Kaydı ve Onayı](satici-kaydi-ve-onayi.md) dosyasında; `ROLE_SELLER` admin onayıyla verilir.
- **Hesap silme, KVKK dışa aktarma ve sosyal giriş yok.** Yönetici ekranı bugün yalnızca satıcı başvuruları için var.
- **Hız sınırı tek örnek içindir** ve istemci adresine dayanır ([ADR-022](../adr/022-in-memory-fixed-window-rate-limiting.md)).

## Açık konular

- Ekranların gerçek tarayıcıda, 375 px ve 1440 px'de, açık ve koyu temada gözle doğrulanması (insan işi; akış curl ile doğrulandı).
- Üretimde `web`'in önünde `X-Forwarded-For` başlığını kendisi yazan bir reverse proxy gerekiyor (KALANLAR B5).
- Hesap askıya alma (`SUSPENDED`) ihtiyacı doğarsa durum enum'u genişleyecek.

## Tarihçe

- 2026-10-09: Oturum tarayıcı kapatılıp açıldığında her sayfada sürüyor (büyük e-ticaret sitelerindeki davranış). Süresi dolmuş oturumla açılan her sayfa oturumu sessizce yeniliyor ve kullanıcı aynı sayfaya oturumu açık olarak dönüyor; yenileme başarısızsa giriş ekranına itilmeden oturumsuz devam ediyor. Sekmeler Web Locks ile sıraya giriyor. `api` kullanılmış yenileme token'ını 30 saniye içinde tekrar kabul ediyor; pencere dışında aile iptali sürüyor. [ADR-069](../adr/069-refresh-token-reuse-grace-window.md), [ADR-070](../adr/070-silent-session-refresh-on-any-page.md); change `change-session-persistence`.
- 2026-10-09: Hata düzeltmesi: erişim token'ı dolmuşken (ör. tarayıcı kapatılıp açıldıktan sonra) uygulama içi bir bağlantıyla korunan sayfaya gidilince ("Hesabım") boş sayfa açılıyordu. Kapı Route Handler'a yönlendiriyordu ve Next.js istemci yönlendiricisi onu izleyemiyordu. Kapı artık `/session/refresh` ara sayfasına yönlendiriyor; sayfa yenileme ucuna tam sayfa gezintisiyle geçiyor. [ADR-023](../adr/023-web-bff-cookies-and-header-csrf.md)'ün yenileme modeli değişmedi. Başsız Chrome'da yeniden üretildi ve düzeltme doğrulandı. Branch `fix/session-refresh-client-navigation`.
- 2026-10-08: "Hesabım" ekranına satıcılık bölümü eklendi: kaydı olmayanı başvuruya davet ediyor, olanın durumunu gösteriyor. Hesaba rol ekleme ve kaldırma geldi (`Account.grantRole` / `revokeRole`); `ROLE_SELLER` admin kararıyla değişir ve erişim token'ına bir sonraki yenilemede yansır ([ADR-044](../adr/044-seller-role-follows-status-with-query-gate.md)). Rolü yetmeyen istek artık ortak hata gövdesiyle `403 AUTH_FORBIDDEN` alıyor (`ApiAccessDeniedHandler`). Başlıkta `ROLE_ADMIN` için "Yönetim" bağlantısı var. Change `add-seller-onboarding`.
- 2026-10-07: `add-password-reset` change'i kullanıcı onayıyla archive edildi (`openspec/changes/archive/2026-10-07-add-password-reset/`). Ön koşullar: delta spec ana spec'e sync edildi, D1–D4 → ADR-033…036, dokümanlar güncel, `api` 102 / `web` 62 test yeşil, docs strict derleme temiz. Taşımada `proposal.md` ve `design.md` içindeki göreli linkler bir seviye uzatıldı.
- 2026-10-07: Parola sıfırlama (change `add-password-reset`). `api`: `POST /accounts/password-resets` (her durumda aynı `202`, kayıtsız adrese e-posta yok, istemci ve e-posta başına hız sınırı) ve `POST /accounts/password-resets/confirmations` (parola değişir, bütün oturumlar ve açık giriş kodları kapanır, e-posta doğrulanır, "parolanız değişti" bildirimi; oturum açmaz). Token 1 saat, tek kullanımlık, ayrı tabloda (`006-create-password-reset-tokens.sql`). `web`: `/forgot-password`, `/reset-password`, giriş formunda "Parolamı unuttum" bağlantısı. Kararlar ADR-033…036; 14 yeni `api` testi, 8 yeni `web` testi.
- 2026-10-07: Hesap sayfalarının adresleri İngilizce oldu: `/kayit` → `/register`, `/giris` → `/login`, `/dogrulama` → `/verify-email`, `/hesabim` → `/account`. Değişikliğe e-postadaki doğrulama ve giriş bağlantıları da dahil. Eski adresler yönlendirilmez: üretim yok, yalnızca geliştirmede gönderilmiş bağlantılar kırılır. Form alanı `id`'leri ve testlerdeki Türkçe yardımcı adları da İngilizce yapıldı (AGENTS.md §1).
- 2026-10-07: Hesap ekranları, sunucu mesajları ve e-postalar Türkçe ve İngilizce (change `add-i18n`). Hesabın dili saklanıyor (`accounts.locale`): kayıtta yazılır, her başarılı girişte güncellenir; doğrulama bağlantısı, giriş kodu ve "hesabınız zaten var" e-postaları hesabın dilinde gider ([ADR-032](../adr/032-account-locale-stored-and-refreshed-on-login.md)). Kayıt ve giriş yanıtları isteğin dilinde ([ADR-031](../adr/031-api-messages-by-accept-language.md)).

- 2026-10-07: `add-account-and-session` change'i kullanıcı onayıyla archive edildi (`openspec/changes/archive/2026-10-07-add-account-and-session/`). Ön koşullar sağlandı: delta spec ana spec'le birebir, D1–D9 → ADR-018…026, dokümanlar güncel, CI run `37589772992` 4/4 yeşil. Açık kalan tek görev 4.9: ekranların gerçek tarayıcıda, 375 / 1440 px ve iki temada gözle doğrulanması (insan işi). Taşımada 22 göreli link bir seviye uzatıldı; delta spec'teki önceden de kırık olan ADR-017 linki düzeltildi.
- 2026-10-07: İlk sürüm (`add-account-and-session`, Faz 1–4). `api`: kayıt (`202`, enumeration güvenli), e-posta doğrulama, yeni doğrulama bağlantısı, giriş (JWT + rotasyonlu opak yenileme), ikinci faktör adımı, yenileme (tekrar kullanımda aile iptali), çıkış, `GET /accounts/me`, bellek içi hız sınırı; 78 test. `web`: BFF Route Handler'ları, `HttpOnly` çerezler, başlık tabanlı CSRF, yönlendirmeyle yenileme, `/kayit`, `/giris` (aynı ekranda kod adımı), `/dogrulama`, `/hesabim`, başlıkta oturum alanı; 30 test. Dev seed'e iki hesap eklendi. Kararlar ADR-018…026.

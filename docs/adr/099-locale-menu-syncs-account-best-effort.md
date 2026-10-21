# [ADR-099] Dil menüsü önce çerezi yazar, hesabın dilini en iyi çabayla BFF üzerinden eşitler

**Tarih:** 2026-10-21
**Durum:** Accepted
**Kaynak:** `add-account-locale-update` design.md D2

---

## Context

Dil menüsü seçimi bir çereze yazar ve sayfayı sunucuda yeniler ([ADR-030](030-locale-from-cookie-then-header.md)). Menü hem oturumu olmayan ziyaretçide hem oturum açık kullanıcıda görünür ve oturum durumunu bilmez.

Token'lar tarayıcıya hiç verilmez. `api` çağrısı yalnızca `web`'in Route Handler'larından (BFF) yapılır ([ADR-008](008-jwt-auth-with-opaque-refresh.md)). Yenileme çerezi yalnızca `/api/auth` altına gider.

[ADR-098](098-account-locale-updated-by-explicit-endpoint.md), hesabın dilini değiştiren `api` ucunu ekler.

## Decision

- `LocaleMenu` seçimde sırasıyla:
  1. çerezi yazar (bugünkü gibi);
  2. BFF ucunu `PUT /api/account/locale` çağırır, gövde `{"locale": "en"}`;
  3. sonuç ne olursa olsun sayfayı yeniler.
- BFF:
  - köken denetimi ve gövde okuma diğer BFF uçlarıyla aynıdır (`handleBffMutation`);
  - tanınmayan değer: `400`;
  - erişim çerezi yok: `api`'ye gitmez, `204`;
  - `api` `401` dönerse (erişim token'ı geçmiş): `204`. Hesabın dili bir sonraki girişte yetişir ([ADR-032](032-account-locale-stored-and-refreshed-on-login.md));
  - diğer `api` hataları aynen iletilir.
- İstemci eşitleme sonucunu yalnızca bekler. Hata kullanıcıya gösterilmez ve dil değişikliğini geri almaz.
- BFF yolu `/api/auth` dışındadır; yenileme çerezi bu uca gitmez.

| Alternatif | Neden reddedildi |
|---|---|
| Çerezi de BFF'te yazmak, istemcinin yalnızca BFF'i çağırması | Ağ hatasında dil hiç değişmez. Çerez yazımı iki yere dağılır. |
| BFF'i `/api/auth` altına koyup `401`'de token yenilemek | Yenileme çerezi daha fazla yola gider. Dil seçimi bir oturum yenileme akışına dönüşür; kazanç, girişe kadar süren küçük bir gecikmeyi kapatmaktır. |
| Menüye oturum bilgisi geçirip anonim ziyaretçide BFF'i çağırmamak | Başlık düzeni oturum bilgisini menüye taşımak zorunda kalır; kazanç küçük bir isteği atlamaktır. |
| Eşitleme hatasında uyarı göstermek | Kullanıcının yapabileceği bir şey yok; dil değişti ve e-postalar en geç sonraki girişte yetişir. |

## Consequences

### Olumlu
- Dil değişikliği ağ veya `api` hatasında da çalışır.
- Token tarayıcıya çıkmaz; menü oturum bilgisi almaz.

### Olumsuz
- Anonim ziyaretçide her dil seçimi `api`'ye gitmeyen küçük bir BFF isteği daha yapar.
- Erişim token'ı geçmişken yapılan seçim hesaba bir sonraki girişe kadar yansımaz.

### Nötr / Trade-off
- Eşitleme sessizdir. Başarısızlık yalnızca sunucu tarafındaki istek kayıtlarında görünür.

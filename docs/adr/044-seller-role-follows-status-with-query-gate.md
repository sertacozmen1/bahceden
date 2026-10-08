# [ADR-044] `ROLE_SELLER` onayda eklenir, askıda kaldırılır; satıcıya özgü sorgular ayrıca `APPROVED` durumunu denetler

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-seller-onboarding` design.md D5

---

## Context

Yetkilendirme kararı erişim token'ındaki rollerle verilir; her istekte veritabanına gidilmez ([ADR-008](008-jwt-auth-with-opaque-refresh.md)). Token 15 dakika geçerlidir; roller her yenilemede güncel kayıttan okunur.

Askı bir güvenlik eylemidir. Rolün token'dan 15 dakikaya kadar geç düşmesi askıyı o süre boyunca etkisiz bırakır.

## Decision

| Geçiş | Rol |
|---|---|
| `PENDING` → `APPROVED` | `ROLE_SELLER` eklenir |
| `APPROVED` → `SUSPENDED` | `ROLE_SELLER` kaldırılır |
| `SUSPENDED` → `APPROVED` | `ROLE_SELLER` yeniden eklenir |
| Ret ve yeniden gönderim | Rol değişmez |

- Rol ve durum aynı transaction'da değişir. Oturumlar kapatılmaz; kullanıcı alıcı olarak devam eder.
- `SellerService.requireApprovedSeller(accountId)`, onaylı satıcıyı `findByAccountIdAndStatus(accountId, APPROVED)` sorgusuyla getirir; bulamazsa `403 SELLER_NOT_APPROVED` döner. Satıcıya özgü her işlem bu kapıdan geçer ve kendi sahiplik sorgusunu bu satıcının kimliğiyle kurar ([ADR-007](007-query-level-ownership-scoping.md)).
- Kapı bu dilimde yazıldı ve `critical` testle sabitlendi. Kullanan ilk uç ürün ilanı change'iyle gelecek.

| Alternatif | Neden reddedildi |
|---|---|
| Rolü yalnızca onayda vermek, askıda bırakmak | Token'daki rol yanlış bilgi verir; `web` satıcı menüsünü askıdaki satıcıya da gösterir. |
| Askıda hesabın bütün oturumlarını kapatmak | Kullanıcıyı alıcı olarak da dışarı atar; üstelik erişim token'ı yine 15 dakika geçerli kalır. |
| Her istekte rolleri veritabanından okumak | ADR-008'in durumsuz modelini bütün uçlar için değiştirir. |

## Consequences

### Olumlu
- Rol "şu an satış yapabilir" anlamını taşır ve ADR-008 modeli korunur.
- Askıdaki satıcı, token'ında rol hâlâ dururken bile satıcıya özgü işlem yapamaz.

### Olumsuz
- Onaydan sonra rol token'a en geç 15 dakikada girer. "Hesabım" kaydı her açılışta taze okuduğu için onay hemen görünür; gecikmeyi yalnızca satıcı paneli fark eder.
- Satıcıya özgü her işlem bir sorgu daha çalıştırır. Satıcı kimliği zaten bu sorguyla bulunduğu için ek maliyet yok.

### Nötr / Trade-off
- Rol ve durum iki ayrı gösterge gibi görünse de durum otoritedir; rol bir önbellektir.

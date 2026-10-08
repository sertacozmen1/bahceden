# [ADR-037] Satıcı başvurusu: beyan + tek belge, manuel admin onayı

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `openspec/KALANLAR.md` A3 (satıcı onboarding ve doğrulama); ürün sahibinin 2026-10-08 kararı

---

## Context

Bugün üreticiler yalnızca geliştirme seed'i ile oluşuyor. `sellers` tablosu bilinçli olarak minimum: hesap ilişkisi, belge ve onay alanı yok ([ADR-013](013-minimal-seller-table-ahead-of-onboarding.md)). Kayıt olan herkes `ROLE_CUSTOMER` alıyor. `ROLE_SELLER` atayan bir akış yok ([ADR-008](008-jwt-auth-with-opaque-refresh.md)).

Ürün vaadi, üreticinin "bir hırdavatçının Hepsiburada'ya kaydolduğu kolaylıkla" kaydolmasıdır (`AGENTS.md` §1). Öte yandan gıda satan bir pazaryeri, satıcının gerçekten üretici olduğunu gösterebilmek zorundadır. Mesafeli satış ve gıda mevzuatı yasal sorumluluk doğurur.

Kısıtlar:

- MVP ölçeği: az sayıda satıcı, tek bölge (`AGENTS.md` §3). Başvuru hacmi elle incelenebilecek kadar küçük.
- Kimlik veya vergi numarasını otomatik doğrulamak için dış servis gerekir. Bütçede böyle bir servis yok (K7).
- TCKN gibi hassas kişisel veriyi toplamak KVKK yükünü artırır (K10). Gerekmedikçe toplanmamalı.
- Satıcıya ödeme yapmak için gereken IBAN, ödeme modeli kararına bağlıdır (A1). O karar açık.

## Decision

1. **Başvuru oturum açmış bir hesaptan yapılır.** Satıcılık ayrı bir hesap değil, mevcut hesaba eklenen bir roldür (tek `accounts` tablosu, [ADR-008](008-jwt-auth-with-opaque-refresh.md)). Bir hesabın en fazla bir satıcı kaydı olur.
2. **Başvuruda beyan edilenler:** vitrinde görünecek üretici adı, şehir ve iletişim telefonu. Telefon yalnızca admin tarafından görülür, vitrine çıkmaz.
3. **Bir belge yüklenir:** ÇKS (Çiftçi Kayıt Sistemi) belgesi **ya da** vergi levhası. Belge türü başvuruda seçilir. Belge yalnızca admin tarafından görülür. Herkese açık bir adresi olmaz ve vitrine çıkmaz. Dosyanın saklanması [ADR-039](039-uploads-on-local-volume-served-by-api.md)'a göre yapılır.
4. **TCKN, VKN ve IBAN bu aşamada alınmaz.** IBAN, A1 kararıyla ayrı bir change'de gelir.
5. **Onay manueldir.** Başvuru `PENDING` durumunda açılır. `ROLE_ADMIN` başvuruyu ya onaylar (`APPROVED`) ya da gerekçe yazarak reddeder (`REJECTED`). Onayla birlikte hesaba `ROLE_SELLER` eklenir. Sonuç, hesabın dilinde e-postayla bildirilir ([ADR-032](032-account-locale-stored-and-refreshed-on-login.md)).
6. **Reddedilen başvuru düzeltilip yeniden gönderilebilir.** Yeniden gönderim, kaydı tekrar `PENDING` durumuna alır. Admin onaylı bir satıcıyı askıya alabilir (`SUSPENDED`). Askıdaki satıcının ilanları vitrinden düşer ([ADR-038](038-listings-publish-immediately-with-admin-takedown.md)).
7. **Durum kayıtları silinmez.** Kim, ne zaman, hangi gerekçeyle onayladı, reddetti ya da askıya aldı, bunlar saklanır. Bu kayıt yasal sorumluluğun denetim izidir.

Durum makinesinin kesin şekli (geçişler, alan adları, admin ekranı) ilgili change'in `design.md` ve spec dosyalarında belirlenir. Bu ADR yalnızca modeli sabitler.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Yalnızca beyan, belge yok | Denetim izi zayıf kalır. "Bu satıcının üretici olduğunu nereden bildiniz?" sorusunun yazılı bir cevabı olmaz. |
| Beyan + TCKN/VKN | Hassas kişisel veri toplanır ama otomatik doğrulama servisi olmadığı için doğrulama yine manuel kalır. Ek bir güvence getirmeden KVKK yükünü artırır. |
| Otomatik onay, admin sonradan askıya alır | Doğrulanmamış bir satıcı ilk satışını yapabilir. Gıda satışında yasal risk yüksek. |
| Satıcı için ayrı hesap tipi veya tablo | İki kimlik yolu oluşur (`AGENTS.md` §15). ADR-008 tek hesap tablosu ve roller modelini zaten seçti. |

## Consequences

### Olumlu
- Üretici, alıcı olarak açtığı hesaptan birkaç alan ve tek bir belgeyle başvurur. Kayıt kolaylığı vaadi korunur.
- Her onayın arkasında bir belge ve bir admin kararı vardır. Yasal sorumluluğun denetim izi oluşur.
- Hassas kimlik verisi toplanmaz. KVKK yüzeyi telefon ve belgeyle sınırlı kalır.

### Olumsuz
- Onay manuel olduğu için satıcı onay için bekler. Başvuru hacmi artarsa admin yükü de artar.
- Belge sahteciliği admin'in dikkatine kalır. Otomatik kontrol yoktur.
- `sellers` tablosu ikinci adımda genişler: hesap ilişkisi, durum ve belge alanları yeni bir changeset ile gelir. ADR-013'ün öngördüğü maliyet budur.
- Admin rolü bugün yalnızca veritabanından verilebiliyor. Admin ekranı bu change'in kapsamına girer.

### Nötr / Trade-off
- **Açık yasal risk:** ÇKS belgesi veya vergi levhasının gıda satışı ve mesafeli satış için yeterli olup olmadığı **hukuk teyidi bekliyor**. Teyit farklı bir belge gerektirirse belge türü listesi genişler. Model aynı kalır.
- Belge kişisel veri içerir (ad, adres). Saklama süresi ve silme talebi (KVKK) ayrı bir karar konusudur. Bu ADR yalnızca belgeye erişimi admin ile sınırlar.

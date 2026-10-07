# [ADR-024] Yetkilendirme JWT iddialarıyla; oturum sahibi `GET /accounts/me` ile taze okunur

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-account-and-session` design.md D7

---

## Context

JWT seçilmesinin sebebi durumsuz yetkilendirmeydi ([ADR-008](008-jwt-auth-with-opaque-refresh.md)): her istekte veritabanına gitmeden karar vermek. Öte yandan arayüzün gösterdiği "kim bağlı, hangi roller, e-posta doğrulandı mı" bilgisi bayat olmamalı.

## Decision

- Erişim token'ı yalnızca üç iddia taşır: `sub` (hesap kimliği), `email` ve `roles`. İmza HS256'dır; anahtar ortamdan okunur ve en az 256 bit değilse uygulama açılmaz.
- Yetkilendirme kararı **yalnızca** doğrulanmış token'daki `roles` iddiasıyla verilir. Roller zaten `ROLE_` önekiyle saklandığı için ikinci önek eklenmez.
- `GET /api/v1/accounts/me` veritabanından **taze** okur: e-posta, roller ve `emailVerified`. Hesap kimliği dönmez.
- Yenilemede üretilen erişim token'ı hesabın **güncel** rollerini taşır.
- Korunan uca token'sız, geçersiz veya süresi dolmuş token'la gelen istek `401` ve `AUTH_REQUIRED` ile ortak hata gövdesini alır.

| Alternatif | Neden reddedildi |
|---|---|
| Rolleri her istekte veritabanından okumak | Durumsuzluk kazancını yok eder. |
| Rolleri yalnızca `me` ucundan vermek | Her yetkilendirme kararı bir veritabanı okuması olur. |
| Token'a ad, adres gibi kişisel veri koymak | JWT imzalıdır ama şifreli değildir; gereksiz kişisel veri taşımak K10'a aykırı. |
| Kara liste ile anında iptal | Ek durum ve her istekte kontrol; 15 dakikalık pencere bugün kabul edilebilir. |

## Consequences

### Olumlu
- Yetkilendirme veritabanına gitmez; ekranda gösterilen bilgi ise her zaman günceldir.
- Rol değişikliği en geç bir sonraki yenilemede (en fazla 15 dakika) yetkiye yansır.

### Olumsuz
- Rol geri alındığında veya hesap askıya alındığında eski yetki token ömrü boyunca (en fazla 15 dakika) geçerli kalır.

### Nötr / Trade-off
- `401` gövdesi güvenlik filtresinde üretilir (`@RestControllerAdvice`'tan önce çalışır). Bunun bir yan etkisi var: kimliksiz bir istek bilinmeyen bir uca gelirse artık `403` yerine `401 AUTH_REQUIRED` alır. Uç yine kapalıdır.

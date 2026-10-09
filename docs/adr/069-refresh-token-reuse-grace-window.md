# [ADR-069] Yenileme token'ı: 30 saniyelik tekrar kullanım toleransı

**Tarih:** 2026-10-09
**Durum:** Accepted
**Kaynak:** `change-session-persistence` design.md D1. [ADR-018](018-refresh-token-rotation-with-family-revocation.md)'in "tolerans penceresi yok" kararının yerini alır; ADR-018'in geri kalanı yürürlüktedir.

---

## Context

[ADR-018](018-refresh-token-rotation-with-family-revocation.md) kullanılmış bir yenileme token'ının her tekrar sunuluşunu sızıntı saydı ve oturum zincirinin tamamını iptal etti. Tolerans penceresini "tek yenileme noktası olan BFF ile ihtiyaç kalmadı" gerekçesiyle reddetti.

[ADR-070](070-silent-session-refresh-on-any-page.md) oturumun yalnızca korunan sayfalarda değil, her sayfada yenilenmesini getiriyor. Tarayıcı kapatılıp açıldığında birkaç sekme birlikte geri yüklenir ve hepsi aynı yenileme çerezini taşır. Sekmeler Web Locks API ile sıraya sokulur, ama bu güvence her durumda geçerli değil:

- API'yi desteklemeyen tarayıcılarda sıralama yapılamıyor.
- Kilidi tutan sekmenin isteği yarıda kesilirse (ağ kopması, sekme kapanması) `api` token'ı tüketmiş olur ama yeni çerez tarayıcıya hiç ulaşmaz. Sonraki deneme eski token'la gelir.

Bu durumlarda meşru kullanıcı çıkış yapmış sayılır. Büyük kimlik sağlayıcıları aynı sorunu kısa bir tolerans penceresiyle çözüyor:

- Okta: varsayılan 30 saniye, 0–60 saniye arası ayarlanabilir.
- Auth0: "reuse interval".

## Decision

- Kullanılmış bir yenileme token'ı, kullanıldığı andan itibaren **30 saniye** (`SessionService.REUSE_GRACE`) içinde tekrar sunulursa reddedilmez. Aynı ailede yeni bir erişim + yenileme çifti verilir.
- Pencere **ilk kullanım anından** sayılır; pencere içindeki tekrar kullanım pencereyi uzatmaz.
- Pencere dışındaki tekrar kullanım ADR-018'deki gibi sızıntıdır: ailenin tamamı iptal edilir.
- İptal edilmiş (çıkış, parola sıfırlama, sızıntı) veya süresi dolmuş token pencere içinde de reddedilir.
- Şema değişmez: pencere `used_at` kolonundan hesaplanır.

| Alternatif | Neden reddedildi |
|---|---|
| Toleranssız kalmak (ADR-018) | Kesilen istek ve kilit desteklemeyen tarayıcı meşru kullanıcıyı çıkarır. Her sayfada yenileme bu olasılığı artırır. |
| Penceredeki tekrar kullanımda aynı ardılı (önceden üretilmiş token'ı) yeniden vermek | Düz token saklanmıyor (yalnızca hash); aynı değeri yeniden vermek düz token'ı saklamayı gerektirir. |
| Yalnızca ardılı henüz kullanılmamış token'a tolerans (Auth0 "yalnızca bir önceki") | Ardıl bağı için yeni kolon ve ek sorgu gerekir. 30 saniyelik pencerede kazanılan güvenlik farkı ihmal edilebilir. |
| 60 saniye veya daha uzun pencere | Sekme yarışı milisaniyeler, kesilen istek birkaç saniye sürer. Uzun pencere saldırgana daha uzun sessiz kullanım alanı verir. |

## Consequences

### Olumlu
- İki sekmenin eşzamanlı yenilemesi veya yarıda kesilen bir yenileme kullanıcıyı oturumdan atmaz.
- Sızıntı tespiti korunur: çalınan token 30 saniyeden sonra kullanılırsa aile yine iptal edilir.

### Olumsuz
- Token'ı ilk 30 saniye içinde çalıp kullanan saldırgan tespit edilmez ve kendi dalını açar. Sızıntı ancak bu dallardan birinin eski token'ı pencere dışında yeniden sunulursa fark edilir.
- Pencere içinde aynı aileden birden çok geçerli token oluşabilir. Çıkış ailenin tamamını iptal ettiği için çıkış yine gerçek çıkıştır.

### Nötr / Trade-off
- Süre bir sabittir, yapılandırma değildir. Değiştirmek bu ADR'nin güncellenmesini gerektirir.

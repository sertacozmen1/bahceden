## ADDED Requirements

### Requirement: Vitrin okumaları kısa süre cache'lenir ve değişiklikte tazelenir

Sistem, vitrin listesinin ilk sayfalarını ve ürün detayını kısa bir süre (yapılandırılmış, varsayılan 60 saniye) SHALL cache'ler. Ürünün vitrindeki görünümünü değiştiren her değişiklik kalıcı olduktan sonra ilgili cache kayıtları silinir. Bu değişiklikler şunlardır: ilan bilgisi, fotoğraflar, yayın durumu, admin kaldırma veya geri alma, satıcının onay durumu ve stok. Geri alınan bir değişiklik cache'i etkilemez. Vitrinde görünmeyen bir ürün için verilen "bulunamadı" yanıtı cache'lenmez. Cache erişilemezse vitrin hata vermez; okuma doğrudan yapılır. Satın alma kararında stok ve fiyat cache'ten değil, ödeme anındaki kilitli okumadan alınır.

#### Scenario: Değişiklik sonraki okumada görünür
- **WHEN** satıcı yayındaki bir ürünün fiyatını günceller
- **THEN** güncelleme kalıcı olduktan sonraki ilk detay ve liste okuması yeni fiyatı gösterir

#### Scenario: Kaldırılan ürün cache'ten dönmez
- **WHEN** admin yayındaki bir ürünü kaldırır veya satıcısı askıya alınır
- **THEN** sonraki detay okuması 404 döner ve ürün vitrin listesinde görünmez

#### Scenario: Geri alınan değişiklik
- **WHEN** bir ilan güncellemesi doğrulama hatasıyla geri alınır
- **THEN** cache'teki görünüm değişmez ve hâlâ kalıcı veriyi yansıtır

#### Scenario: Cache erişilemez
- **WHEN** cache servisi o anda erişilemezdir
- **THEN** vitrin listesi ve ürün detayı yine doğru verilerle döner

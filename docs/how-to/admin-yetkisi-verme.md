# Nasıl Yapılır: Admin yetkisi verme ve satıcı başvurusunu karara bağlama

**Özet:** Bu rehber, bir hesaba `ROLE_ADMIN` vermeyi, rolü geri almayı ve admin ekranından satıcı başvurularını karara bağlamayı anlatır. `ROLE_ADMIN` ürün yüzeyinden verilemez; bilinçli olarak yalnızca veritabanından atanır ([ADR-046](../adr/046-admin-surface-in-web-first-admin-by-sql.md)).

## Ön koşullar

- Veritabanına yazma yetkisi. Geliştirmede `docker compose` ortamı yeterli; üretim erişimi barındırma kararına bağlı (KALANLAR B5).
- Admin yapılacak kişinin Bahçeden'de hesabı var ve e-postası biliniyor.
- Okunmuş olması gerekenler: [ADR-037](../adr/037-seller-application-with-document-and-manual-approval.md) (başvuru ve onay modeli), [ADR-044](../adr/044-seller-role-follows-status-with-query-gate.md) (rol ve token gecikmesi).

> [!NOTE]
> Geliştirme ortamında admin hesabı hazırdır: `admin@ornek.test` (parola `gelistirme-parolasi-1`; yalnızca dev seed'inde, [Yerel geliştirme](yerel-gelistirme.md) adım 7). Aşağıdaki adımlar gerçek bir hesaba rol vermek içindir.

## Adımlar

### 1. Rolü verin

Geliştirmede:

```bash
docker compose exec -T db psql -U bahceden -d bahceden -c \
  "insert into account_roles (account_id, role) select id, 'ROLE_ADMIN' from accounts where email = 'kisi@ornek.test' on conflict (account_id, role) do nothing;"
```

Komut `INSERT 0 1` döndürmeli. `INSERT 0 0` dönerse ya o e-postayla hesap yoktur ya da rol zaten vardır. E-posta küçük harfle yazılır; hesaplar normalleştirilmiş e-postayla saklanır.

### 2. Kişiye yeniden giriş yaptırın

Roller erişim token'ına girişte ve her yenilemede yazılır. Rol, kişinin açık oturumuna en geç 15 dakika içinde kendiliğinden yansır. Hemen kullanması gerekiyorsa çıkış yapıp yeniden giriş yapmalıdır. Rol yansıyınca başlıkta **Yönetim** bağlantısı görünür.

### 3. Başvuruları karara bağlayın

1. **Yönetim** bağlantısını veya `/admin/sellers` adresini açın. Varsayılan sekme **Bekleyen**dir.
2. Başvuruyu açın. Detay ekranında hesap e-postası, telefon, şehir, belge türü ve geçmiş bulunur.
3. **Belgeyi yeni sekmede aç** ile belgeyi inceleyin. Belge yalnızca adminlere ve sahibine sunulur.
4. Kararı verin:
   - **Onayla:** kayıt `APPROVED` olur, hesaba `ROLE_SELLER` eklenir.
   - **Reddet:** 10–500 karakterlik bir gerekçe ister. Gerekçe başvurana gösterilir; başvuran düzeltip yeniden gönderebilir.
   - Onaylı satıcıda **Askıya al:** gerekçe ister. Rol kaldırılır ve ürünler vitrinden düşer.
   - Askıdaki satıcıda **Askıdan çıkar:** rol ve ürünler geri gelir.

Her karar başvurana, hesabının dilinde e-postayla bildirilir. Geliştirmede bu e-postalar `http://localhost:8025` adresindeki mailpit'te görünür.

### 4. Rolü geri alın

```bash
docker compose exec -T db psql -U bahceden -d bahceden -c \
  "delete from account_roles where role = 'ROLE_ADMIN' and account_id = (select id from accounts where email = 'kisi@ornek.test');"
```

> [!IMPORTANT]
> Erişim token'ı iptal edilemez; yalnızca yenilenemez ([ADR-008](../adr/008-jwt-auth-with-opaque-refresh.md)). Rolü alınan kişi, elindeki token'ın kalan süresi boyunca (en fazla 15 dakika) admin uçlarına erişmeye devam edebilir. Acil durumda kişinin parolasını sıfırlamak bütün oturumlarını kapatır ([ADR-035](../adr/035-password-reset-signs-out-everywhere-no-auto-login.md)).

## Olası hatalar

- **Hata:** Rol verildi ama **Yönetim** bağlantısı görünmüyor; `/admin/sellers` "bulunamadı" diyor.
  **Çözüm:** Token henüz yenilenmedi. Kişi çıkış yapıp yeniden giriş yapsın (adım 2).

- **Hata:** Admin eylemi `403 AUTH_FORBIDDEN` dönüyor.
  **Çözüm:** Aynı sebep: token'da rol yok. Rolün veritabanında olduğunu doğrulayın: `select role from account_roles where account_id = (select id from accounts where email = '…');`

- **Hata:** Onay düğmesi `409 SELLER_INVALID_TRANSITION` dönüyor.
  **Çözüm:** Kayıt o eylem için uygun durumda değil. Örneğin reddedilen bir kayıt onaylanamaz; başvuranın yeniden göndermesi gerekir. Sayfayı yenileyip güncel durumu kontrol edin.

## İlgili kararlar

- [ADR-037](../adr/037-seller-application-with-document-and-manual-approval.md) — beyan + belge, manuel admin onayı
- [ADR-044](../adr/044-seller-role-follows-status-with-query-gate.md) — rol ve onaylı satıcı kapısı
- [ADR-045](../adr/045-seller-documents-table-content-sniffing-protected-serving.md) — belgenin saklanması ve korumalı sunumu
- [ADR-046](../adr/046-admin-surface-in-web-first-admin-by-sql.md) — admin yüzeyi; ilk admin veritabanından

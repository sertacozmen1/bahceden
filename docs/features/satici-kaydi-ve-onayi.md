# Satıcı Kaydı ve Onayı

**Durum:** Geliştirmede
**İlk sürüm:** 2026-10-08
**Son değişiklik:** 2026-10-08

| | |
|---|---|
| **Ürün belgesi** | [Satıcı Kaydı ve Onayı](../product/satici-kaydi.md) |
| **Kararlar** | Çerçeve: [ADR-037](../adr/037-seller-application-with-document-and-manual-approval.md), [ADR-038](../adr/038-listings-publish-immediately-with-admin-takedown.md), [ADR-039](../adr/039-uploads-on-local-volume-served-by-api.md). Uygulama: [ADR-040](../adr/040-seller-application-is-the-seller-record.md) … [ADR-046](../adr/046-admin-surface-in-web-first-admin-by-sql.md) |
| **Spec (capability)** | `openspec/specs/seller-onboarding` (yeni); `openspec/specs/storefront-catalog` (vitrin satıcı durumu süzgeci) |
| **Change'ler** | `openspec/changes/add-seller-onboarding` (açık; archive kullanıcı onayı bekliyor) |
| **Rehberler** | [Admin yetkisi verme ve başvuruyu karara bağlama](../how-to/admin-yetkisi-verme.md), [Yerel geliştirme](../how-to/yerel-gelistirme.md) |
| **Referans** | [Domain modeli](../reference/01-domain-model.md), [API referansı](../reference/02-api-reference.md), [Tasarım sistemi](../reference/03-design-system.md) |
| **Kod** | `api/src/main/java/com/bahceden/seller/`, `api/.../common/storage/`, `web/src/app/(seller)/`, `web/src/app/(admin)/`, `web/src/app/api/seller/`, `web/src/app/api/admin/`, `web/src/features/seller/`, `web/src/features/admin/` |

---

## Ne yapar

Bir üretici, alıcı olarak açtığı hesaptan "Hesabım → Satıcı olarak başvur" yolunu izler. Formda üretici adını, şehrini ve telefonunu yazar, ÇKS belgesini ya da vergi levhasını yükler. Başvuru için e-posta adresinin doğrulanmış olması gerekir, çünkü karar e-postayla gelir. Yönetici bekleyen başvuruları bir listede görür, belgeyi açar ve başvuruyu onaylar ya da gerekçe yazarak reddeder. Onaylanan hesap satıcı rolünü alır. Reddedilen üretici gerekçeyi görür, bilgilerini düzeltip yeni bir belgeyle yeniden gönderir. Yönetici onaylı bir satıcıyı gerekçeyle askıya alabilir. Askıdaki satıcının ürünleri vitrinde görünmez; askıdan çıkınca geri gelir.

## Bilinçli sınırlar

- **Ürün ilanı ekleme yok.** Onaylı satıcının bugün yapabileceği bir satış işlemi yok; ilan ve fotoğraf ayrı bir change olacak ([ADR-038](../adr/038-listings-publish-immediately-with-admin-takedown.md), [ADR-039](../adr/039-uploads-on-local-volume-served-by-api.md)).
- **Admin'e yeni başvuru bildirimi yok.** Bildirim kanalları KALANLAR B4'e bağlı; admin listeyi panelden izler.
- **Profil düzenleme yok.** Onaylı satıcı adını, şehrini ve telefonunu değiştiremez.
- **TCKN, VKN, IBAN alınmıyor** ([ADR-037](../adr/037-seller-application-with-document-and-manual-approval.md) §4). IBAN ödeme kararıyla (A1) gelecek.
- **Belge otomatik doğrulanmıyor ve antivirüsten geçmiyor.** Tür içerikten tespit ediliyor ve belge korumalı başlıklarla sunuluyor ([ADR-045](../adr/045-seller-documents-table-content-sniffing-protected-serving.md)).
- **`ROLE_ADMIN` ürün yüzeyinden verilemez** ([ADR-046](../adr/046-admin-surface-in-web-first-admin-by-sql.md)).
- **HEIC ve WebP desteklenmiyor**; telefondan JPEG veya PNG istenir.

## Açık konular

- **Hukuk:** ÇKS belgesi ve vergi levhası yeterli mi? Başvuruda KVKK aydınlatma metni gerekiyor mu? Belgeler ne kadar saklanacak? (KALANLAR A5)
- Gerçek cihazda insan doğrulaması. Başsız Chrome ile 375 / 1440 px ve iki temada ekran görüntüsü alındı.
- Change `add-seller-onboarding`'in archive'ı kullanıcı onayı bekliyor.

## Tarihçe

- 2026-10-08: İlk sürüm (change `add-seller-onboarding`).
  - `api`:
    - başvuru, yeniden gönderim, kendi kaydı ve belgesi (`/api/v1/sellers/me`);
    - admin listesi, detayı, belgesi ve dört karar (`/api/v1/admin/sellers`);
    - `ROLE_SELLER`'ın onayda eklenip askıda kaldırılması;
    - hesabın dilinde karar e-postaları;
    - silinmeyen durum geçmişi;
    - vitrinde satıcı durumu süzgeci;
    - `FileStorage` ile yerel volume'a dosya yazma.
  - Şema: `007-seller-onboarding.sql`, `008-dev-seed-seller-onboarding.sql`.
  - `web`:
    - `/seller/application` (form ve durum ekranları);
    - "Hesabım"da satıcılık kartı;
    - `/admin/sellers` ve `/admin/sellers/[id]`;
    - başlıkta "Yönetim" bağlantısı;
    - BFF'te multipart ve belge akışı.
  - Testler: 133 `api` (+31), 93 `web` (+31).
  - Doğrulama: `docker compose` üzerinde başvuru → ret → yeniden gönderim → onay → askı → askıdan çıkarma uçtan uca denendi; iki dilde e-posta ve volume kalıcılığı kontrol edildi.
  - Kararlar: ADR-040…046.

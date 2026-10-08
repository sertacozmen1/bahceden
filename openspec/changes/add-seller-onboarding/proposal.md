# add-seller-onboarding

**Durum:** Onaylandı (2026-10-08, proje sahibi `/opsx:apply`); uygulanıyor.

> Bu change, `openspec/KALANLAR.md` A3'ün karara bağlanmasıyla ([ADR-037](../../../docs/adr/037-seller-application-with-document-and-manual-approval.md), [ADR-038](../../../docs/adr/038-listings-publish-immediately-with-admin-takedown.md)) ve B3'ün ([ADR-039](../../../docs/adr/039-uploads-on-local-volume-served-by-api.md)) üzerine kurulur. [`00-current-state.md`](../../../docs/reference/00-current-state.md) L6 ve L7 sınırlamalarını kapatır.

## Why

Bugün üretici yalnızca geliştirme seed'i ile oluşabiliyor. `sellers` tablosunun hesapla bir ilişkisi yok ([ADR-013](../../../docs/adr/013-minimal-seller-table-ahead-of-onboarding.md)) ve `ROLE_SELLER` atayan bir akış bulunmuyor. Ürünün vaadi, üreticinin kolayca kaydolup ürün listelemesi. Bu vaat, satıcının sisteme girebildiği bir kapı olmadan başlayamaz.

Sıradaki dilim olan ürün ilanı ekleme de buna bağlı. Satıcı paneli, sahiplik kontrolü ([ADR-007](../../../docs/adr/007-query-level-ownership-scoping.md)) ve ilan yayını ([ADR-038](../../../docs/adr/038-listings-publish-immediately-with-admin-takedown.md)), hesapla bağlanmış ve onaylanmış bir satıcı kaydı ister.

## What Changes

- **`api`:**
  - **Yeni `seller` domain paketi.** `Seller` entity'si `catalog`'dan buraya taşınır (`AGENTS.md` §11.2).
  - **Başvuru:** oturum açmış ve e-postası doğrulanmış bir hesap tek bir multipart istekle başvurur. İstekte beyan alanları (üretici adı, şehir, telefon), belge türü (ÇKS belgesi veya vergi levhası) ve belge dosyası bulunur. Kayıt `PENDING` durumunda açılır.
  - **Kendi kaydını görme ve yeniden gönderme:** hesap kendi satıcı kaydını görür. Kayıt `REJECTED` ise düzeltip yeniden gönderir ve kayıt tekrar `PENDING` olur. Hesap kendi belgesini indirebilir.
  - **Admin uçları** (`ROLE_ADMIN`): satıcıları duruma göre süzerek listeleme, detay, belge indirme, onay, gerekçeli ret, gerekçeli askıya alma ve askıdan çıkarma.
  - **Rol:** onayla hesaba `ROLE_SELLER` eklenir. Askıya alındığında rol kaldırılır, askıdan çıkarıldığında geri eklenir.
  - **Bildirim:** her karar hesabın dilinde e-postayla bildirilir.
  - **Denetim izi:** her durum geçişi silinmeyen bir kayıt olarak saklanır: kim yaptı, ne zaman, hangi durumdan hangisine, gerekçesi ne.
  - **Dosya depolama:** belgeler [ADR-039](../../../docs/adr/039-uploads-on-local-volume-served-by-api.md)'a göre yapılandırılmış bir dizinde (Docker volume) saklanır. Dosyanın türü içeriğindeki imzadan belirlenir; kabul edilenler PDF, JPEG ve PNG'dir, boyut sınırı yapılandırmadadır.
  - **Vitrin:** yalnızca `APPROVED` durumundaki satıcıların ürünleri vitrinde görünür ([ADR-038](../../../docs/adr/038-listings-publish-immediately-with-admin-takedown.md) §4).
- **`web`:**
  - "Hesabım" ekranına satıcılık kartı eklenir. Hesabın satıcı kaydı yoksa kart başvuruya davet eder, varsa kaydın durumunu gösterir.
  - `(seller)` rota grubunda `/seller/application` sayfası: başvuru formu, durum ekranı ve ret gerekçesiyle yeniden gönderme.
  - `(admin)` rota grubunda iki sayfa: `/admin/sellers` (duruma göre süzülen liste) ve `/admin/sellers/[id]` (detay, belgeyi görme, karar eylemleri). Gerekçe isteyen eylemler onay penceresiyle yapılır.
  - BFF Route Handler'ları: başvuru (multipart iletimi), belge indirme (akış olarak iletim), admin eylemleri. Hepsi mevcut CSRF başlığı denetimini kullanır.
  - Metinler `tr.json` ve `en.json` dosyalarına eklenir.
- **Sözleşme:** yeni uçlar ve şemalar eklenir. Kırıcı değişiklik yoktur. `ProductSummary` değişmez; vitrin yalnızca daha az kayıt döndürebilir.
- **Altyapı:** `docker-compose.yml`'e `api` için adlandırılmış bir `uploads` volume'u eklenir. `api` imajında yükleme dizini oluşturulur ve uygulama kullanıcısına verilir. Yeni bir ortam değişkeni eklenir: `BAHCEDEN_STORAGE_DIR`.

## Capabilities

- **Yeni:** `seller-onboarding` (delta: `specs/seller-onboarding/spec.md`): başvuru, belge, admin kararları, rol, bildirim, denetim izi.
- **Değişen:** `storefront-catalog` (delta: `specs/storefront-catalog/spec.md`): "Vitrinde yayınlanmış ürünlerin listelenmesi" gereksinimine satıcı durumu şartı eklenir (MODIFIED).

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | Yeni paket `com.bahceden.seller`: `Seller` (taşındı), `SellerStatus`, `SellerDocument`, `SellerStatusChange`, `SellerService`, `SellerController`, `AdminSellerController`, `SellerMapper`, repository'ler, DTO'lar. Ayrıca: `common/storage/FileStorage` (disk okuma/yazma ve imzadan tür tespiti); `BahcedenProperties.storage`; `ProductRepository.findPublished` (satıcı durumu filtresi); `Account` (rol ekleme/çıkarma); `SecurityConfig` (yeni uçlar); `RateLimitPolicy.SELLER_APPLICATION`; `messages*.properties`; sözleşme testi ve `critical` testler |
| `web/` | Evet | `(seller)/seller/application`, `(admin)/admin/sellers`, `(admin)/admin/sellers/[id]`; `features/seller` (bileşenler, `api.ts`); `AccountScreen` satıcılık kartı; `bffClient` multipart desteği; BFF Route Handler'ları; `requireRole` kapısı; `messages/*.json`; testler |
| DB | Evet | `007-seller-onboarding.sql`. `sellers` tablosuna `account_id` (tekil, NULL olabilir), `status`, `phone`, karar alanları eklenir. Mevcut satırlar `APPROVED` olur. Yeni tablolar: `seller_documents`, `seller_status_changes`. `--rollback` eklenenleri geri alır. Veri kaybı yoktur, `NOT NULL` yalnızca varsayılanı olan yeni kolonlara eklenir. `008-dev-seed-seller-onboarding.sql` (dev bağlamı): bir admin hesabı ve satıcı kaydı olmayan, e-postası doğrulanmış bir üretici hesabı |
| `shared/` | Evet (ekleyici) | Yeni uçlar ve şemalar; `openapi.yaml` yeniden üretilir |
| `docs/` | Evet | Design D1–D7 → ADR-040…046; `docs/product/satici-kaydi.md`; yeni feature dosyası `docs/features/satici-kaydi-ve-onayi.md`; `01-domain-model.md` (ER), `02-api-reference.md`; how-to: "Admin yetkisi verme"; `yerel-gelistirme.md` (volume, yeni değişken); `00-current-state.md` (L6/L7 kalkar, yeni sınırlamalar) |
| Altyapı | Evet | `docker-compose.yml`: `uploads` volume'u ve `BAHCEDEN_STORAGE_DIR`. Bu, servis yapısı değişikliğidir ve bu proposal'ın onayı bu değişikliğin onayıdır (`AGENTS.md` §9). `api/Dockerfile`: yükleme dizini. `api/.env.example` |

## Bağımlılık

Yeni paket yok (K7). Multipart desteği Spring MVC'de, dosya imzası tespiti elle yazılan birkaç bayt karşılaştırmasıyla, e-posta mevcut `MailSender` ile yapılır. `web` tarafında `FormData` ve `fetch` tarayıcı API'leridir.

## Kişisel veri (K10)

Bu change **yeni kişisel veri** topluyor (`AGENTS.md` §9: onay gerektirir):

- **Telefon:** yalnızca admin görür. Vitrine ve sözleşmenin herkese açık şemalarına çıkmaz. Log'a yalnızca son 4 hanesi yazılır.
- **Belge** (ÇKS belgesi veya vergi levhası; ad, adres ve kimlik numarası içerebilir): yalnızca admin ve belgenin sahibi görür. Herkese açık bir adresi yoktur. Dosya adı sunucuda üretilir, içerik log'a yazılmaz.
- **Ret ve askı gerekçesi:** satıcıya gösterilir ve e-postada geçer.
- Testler, seed ve fixture'larda yalnızca sahte veri kullanılır (`ornek.test`, `+90 555 000 00 00`, sahte PDF baytları).

Saklama süresi, silme talebi ve aydınlatma metni hukuk teyidi bekliyor (bkz. design.md Open Questions).

## Non-goals

- **Ürün ilanı ekleme, düzenleme ve fotoğraf.** Ayrı change; bu change'in kurduğu `FileStorage` ve satıcı kaydı ona temel olur.
- **Admin'in ilanı yayından kaldırması** ([ADR-038](../../../docs/adr/038-listings-publish-immediately-with-admin-takedown.md) §3). Satıcının ilanı olmadan kaldırılacak bir şey yok; ilan change'iyle gelir.
- Onaylı satıcının profilini (ad, şehir, telefon) düzenlemesi. Gerekirse küçük bir sonraki dilim.
- Admin'e yeni başvuru bildirimi (e-posta veya SMS). Bildirim kanalları KALANLAR B4'e bağlı; admin bekleyen listeyi panelden görür.
- TCKN, VKN ve IBAN toplamak ([ADR-037](../../../docs/adr/037-seller-application-with-document-and-manual-approval.md) §4).
- Belge türünü veya kimliği otomatik doğrulamak (dış servis yok).
- `ROLE_ADMIN`'i ürün yüzeyinden vermek. İlk admin veritabanından atanır; bunun için how-to yazılır.
- Satıcı kaydını veya belgeyi silmek. KVKK silme talebi süreci hukuk kararından sonra ayrı bir iş.
- HEIC ve WebP desteği ([ADR-039](../../../docs/adr/039-uploads-on-local-volume-served-by-api.md)).

## Riskler

- **Rol değişikliği gecikmesi:** onay sonrası `ROLE_SELLER`, erişim token'ına bir sonraki yenilemede (en fazla 15 dakika) girer. Askıya almada rol token'dan aynı süre içinde düşer. Bu arada satıcıya özgü her sorgu satıcının `APPROVED` olduğunu da denetler (D5), yani askıdaki satıcı bu pencerede de işlem yapamaz.
- **Belgenin yasal yeterliliği** hukuk teyidi bekliyor ([ADR-037](../../../docs/adr/037-seller-application-with-document-and-manual-approval.md)). Belge türü listesi genişleyebilir; model aynı kalır.
- **Yüklenen dosya saldırı yüzeyidir.** Bunu sınırlayan önlemler: tür içerikten tespit edilir, boyut sınırı vardır, ad sunucuda üretilir, belge yanıtı `nosniff` ve `Content-Security-Policy: sandbox` başlıklarıyla döner, belge yalnızca yetkili kişilere sunulur. Antivirüs taraması yok; bu, kabul edilen bir risk olarak `00-current-state.md`'ye yazılır.
- **Volume yedeklenmiyor.** Yedekleme B5'e bağlı ([ADR-039](../../../docs/adr/039-uploads-on-local-volume-served-by-api.md)).

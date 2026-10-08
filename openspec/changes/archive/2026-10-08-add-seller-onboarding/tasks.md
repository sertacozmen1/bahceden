# Tasks: add-seller-onboarding

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7). Şema değişikliği ekleyicidir: yeni kolonlar ve tablolar, veri kaybı yok.
> `docker-compose.yml` değişikliği (`uploads` volume'u) bu change'in onayıyla onaylanmış sayılır (§9).

## Faz 0 · Ürün belgesi

- [x] 0.1 `docs/product/satici-kaydi.md` (şablondan): kullanıcı problemi, değer önerisi, rakip analizi (büyük pazaryerlerinin satıcı başvuru akışları), kapsam ve kabul kriterleri (spec senaryolarıyla eşleşir), başarı metrikleri (başvurudan karara geçen süre, ret oranı). `docs/product/index.md` tablosu.

## Faz 1 · `seller` paketi, şema ve vitrin filtresi

Bu faz bittiğinde vitrin aynen çalışır, yalnızca `APPROVED` satıcıların ürünlerini gösterir.

- [x] 1.1 `007-seller-onboarding.sql` (design.md Migration Plan 1; `--rollback` dahil) ve master changelog'a `include`.
- [x] 1.2 `Seller`'ı `com.bahceden.seller.domain`'e taşı. Yeni alanlar, `SellerStatus` enum'u, `SellerDocument` ve `SellerStatusChange` entity'leri, `SellerRepository` (`findByAccountId`, `findByAccountIdAndStatus`, durum süzgeçli sayfalı liste), `SellerDocumentRepository` (kaydın en son belgesi), `SellerStatusChangeRepository` (yalnızca ekleme ve sıralı okuma). `catalog` import'larını güncelle.
- [x] 1.3 `ProductRepository.findPublished` ve sayım sorgusuna satıcı durumu koşulu (D2).
- [x] 1.4 **Test (`critical`):** askıdaki ve bekleyen satıcının `PUBLISHED` ürünü vitrinde ve toplam sayıda yok; askıdan çıkınca geri geliyor; vitrin sorgu sayısı testi hâlâ tek sorgu. Mevcut `ProductControllerTest` ve `ProductRepositoryTest` yeşil. `DevSeedIsolationTest` yeni changeset'le de yeşil.
- [x] 1.5 **Doğrulama:** `./mvnw -B verify` temiz.

## Faz 2 · Başvuru: `api` → sözleşme → `web`

Bu faz bittiğinde kullanıcı başvurur ve "Hesabım"da kaydının durumunu görür. Kararı veren yüzey henüz yoktur; durum `PENDING` kalır.

- [x] 2.1 `common/storage/FileStorage` (D6): yazma, okuma, silme, imzadan tür tespiti. `BahcedenProperties.storage` (`dir`, `maxDocumentBytes`) ve açılışta dizin doğrulaması. `spring.servlet.multipart` sınırları. `api/.env.example` ve `application.yaml`'a `BAHCEDEN_STORAGE_DIR`. Aynı adımda `api/Dockerfile` (yükleme dizini ve sahipliği) ve `docker-compose.yml` (`uploads` volume'u, `BAHCEDEN_STORAGE_DIR`): değişken zorunlu olduğu için compose bu fazda güncellenmezse ortam açılmaz (K5).
- [x] 2.2 `SellerService.apply`, `resubmit`, `findOwn`, `ownDocument` (D3, D4). Telefon normalleştirme, `slug` üretimi, dosya-transaction tutarlılığı (geri almada dosyayı sil), durum geçişi kaydı. `SellerService.requireApprovedSeller` kapısı (D5). Log'da telefon son 4 hane, e-posta maskeli.
- [x] 2.3 DTO'lar (`@Schema` açıklama ve örnek): `SellerApplicationRequest`, `SellerSelfView`, `SellerStatus` ve `SellerDocumentType` enum'ları. `SellerController`: `GET/POST/PUT /api/v1/sellers/me`, `GET /api/v1/sellers/me/document` (korumalı başlıklar). `SecurityConfig` girdileri. `RateLimitPolicy.SELLER_APPLICATION`. Yeni `ApiErrorCode` değerleri.
- [x] 2.4 `messages.properties` ve `messages_en.properties`: doğrulama mesajları, hata mesajları (`MessagesCatalogTest`).
- [x] 2.5 `openapi.yaml` yeniden üretilir (`OpenApiContractTest`). Multipart şemasının üretilen çıktısı kontrol edilir; anlamsız ad varsa `@Schema(name=…)` ile düzeltilir.
- [x] 2.6 **Test (`critical`):**
  - başvuru başarılı;
  - 401 (oturum yok);
  - 422 (e-posta doğrulanmamış);
  - 409 (ikinci başvuru);
  - alan doğrulama hataları ve hiçbir dosyanın yazılmaması;
  - uzantısı `.pdf` ama içeriği PDF olmayan dosya;
  - boyut sınırı;
  - kendi belgesi korumalı başlıklarla dönüyor;
  - başkasının belgesi 404;
  - `REJECTED` dışındaki kayıtta yeniden gönderim 409;
  - yeniden gönderim önceki belgeyi koruyor;
  - `requireApprovedSeller` yalnızca `APPROVED`'u kabul ediyor;
  - hız sınırı;
  - durum geçişi kaydı yazılıyor.

  Dosyalar test profilinde `target/test-uploads` altına yazılır (`mvn clean` siler); sahte PDF baytları kullanılır.
- [x] 2.7 **Doğrulama:** `./mvnw -B verify` temiz.
- [x] 2.8 `npm run gen:api`. `features/seller/api.ts`: sunucu tarafında kendi kaydını okuma. `bffClient` multipart desteği (mevcut `postToBff` genişletilir, ikinci bir sarmalayıcı yazılmaz). BFF Route Handler'ları:
  - `/api/seller/application`: POST ve PUT; multipart iletimi, CSRF başlığı, `Accept-Language`;
  - `/api/seller/application/document`: GET; akış olarak iletim, başlıklar korunur.
- [x] 2.9 `(seller)/seller/application` sayfası: `layout.tsx` ile `requireSignedIn`, `loading.tsx`, `error.tsx`. İçerik kaydın durumuna göre değişir:
  - kayıt yoksa form: belge türü seçimi, dosya alanı, istemci tarafında boyut ve tür için erken uyarı;
  - `PENDING`: bekleme ekranı;
  - `REJECTED`: ret gerekçesi ve önceden doldurulmuş form;
  - `APPROVED`: onay ekranı; ilan özelliği gelene kadar "yakında" değil, yalnızca durum bilgisi;
  - `SUSPENDED`: askı gerekçesi.

  E-posta doğrulanmamışsa form yerine doğrulama yönlendirmesi ve mevcut `ResendVerificationButton` gösterilir.
- [x] 2.10 `AccountScreen`'e satıcılık kartı: başvuruya davet veya durum ve `/seller/application` bağlantısı. Metinler `tr.json` ve `en.json`'a.
- [x] 2.11 **Test (web):** form doğrulama hatası, sunucu `fieldErrors` eşlemesi (`document` alanı dahil), başarı yolu; her durum ekranı; doğrulanmamış e-posta yönlendirmesi; Route Handler'lar CSRF başlığı olmadan reddediyor; anahtar eşitliği testi.
- [x] 2.12 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz.

## Faz 3 · Admin kararları: `api` → sözleşme → `web`

Bu faz bittiğinde admin başvuruyu karara bağlar, satıcı rolünü alır ve e-postayla bilgilendirilir.

- [x] 3.1 `SellerService.approve`, `reject`, `suspend`, `reinstate` (D5): geçiş tablosu tek yerde (`SellerStatus` üzerinde izin verilen geçişler), rol değişikliği ve durum geçişi kaydı aynı transaction'da, karar e-postası hesabın dilinde ve gönderim hatası akışı kesmez. Admin listesi ve detayı: hesap e-postası, telefon, güncel belge türü ve geçmiş; N+1 yok.
- [x] 3.2 DTO'lar: `AdminSellerSummary`, `AdminSellerDetail`, `SellerStatusChangeView`, `SellerDecisionReason`. `AdminSellerController`: `GET /api/v1/admin/sellers?status=&page=&size=`, `GET /{id}`, `GET /{id}/document`, `POST /{id}/approve|reject|suspend|reinstate`. `SecurityConfig`: `/api/v1/admin/**` için `hasRole("ADMIN")`.
- [x] 3.3 `messages*.properties`: dört karar e-postası (`mail.sellerApproved.*`, `mail.sellerRejected.*`, `mail.sellerSuspended.*`, `mail.sellerReinstated.*`) ve hata mesajları.
- [x] 3.4 `008-dev-seed-seller-onboarding.sql` (design.md Migration Plan 2) ve `DevSeedIsolationTest` kapsamı.
- [x] 3.5 `openapi.yaml` yeniden üretilir.
- [x] 3.6 **Test (`critical`):**
  - dört geçişin her biri ve rol etkisi;
  - geçersiz geçiş 409;
  - gerekçesiz ret ve askı 400;
  - admin olmayan hesap 403, oturum yok 401;
  - süzgeç ve sayfa sınırı;
  - detayda sıralı geçmiş;
  - İngilizce hesaba İngilizce karar e-postası;
  - e-posta hatasında karar yine kaydediliyor;
  - liste sorgu sayısı;
  - onaydan sonra yenilenen erişim token'ı `ROLE_SELLER` içeriyor, askıdan sonra içermiyor.
- [x] 3.7 **Doğrulama:** `./mvnw -B verify` temiz.
- [x] 3.8 `npm run gen:api`. `requireRole` kapısı (`requireSignedIn` genişletilir; rol yoksa `notFound()`). `features/admin/api.ts`. BFF Route Handler'ları:
  - `/api/admin/sellers/[id]/[action]`: POST; eylem beyaz listeyle doğrulanır;
  - `/api/admin/sellers/[id]/document`: GET.
- [x] 3.9 `(admin)/admin/sellers` (durum sekmeleri, varsayılan `PENDING`, boş durum, sayfalama) ve `(admin)/admin/sellers/[id]`:
  - detay ve belgeyi yeni sekmede açma;
  - geçmiş;
  - eylemler: onay birincil eylem; ret ve askı yıkıcı eylem olarak uzakta durur ve gerekçe isteyen onay penceresiyle yapılır (§12.5).

  Dört durumun hepsi tasarlanır. Başlıkta admin için bağlantı gösterilir; bağlantı rol yoksa görünmez.
- [x] 3.10 **Test (web):** gerekçe formu doğrulaması ve sunucu hatası eşlemesi; eylem sonrası ekran güncellemesi; rolsüz kullanıcıya bulunamadı sayfası; Route Handler'da bilinmeyen eylem ve eksik CSRF başlığı reddi.
- [x] 3.11 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz.

## Faz 4 · Uçtan uca doğrulama ve doküman

- [x] 4.1 `docker compose up --build` ile uçtan uca senaryo:
  1. `uretici@ornek.test` başvurur;
  2. `admin@ornek.test` belgeyi görür ve gerekçeyle reddeder;
  3. mailpit'te ret e-postası gelir;
  4. üretici yeniden gönderir;
  5. admin onaylar;
  6. üreticinin oturumu yenilendiğinde `roles` içinde `ROLE_SELLER` vardır;
  7. admin satıcıyı askıya alır, rol düşer;
  8. askıdan çıkarır.

  İki dilde e-posta kontrolü. Konteyner yeniden başlatıldığında belgenin volume'da durduğu kontrol edilir.
- [x] 4.2 Ekranların 375 ve 1440 px'de, açık ve koyu temada başsız tarayıcı ekran görüntüsü: başvuru formu, durum ekranları, admin listesi, admin detayı, gerekçe penceresi.
- [x] 4.3 Design D1–D7 → ADR-040…046 ve `docs/adr/index.md`, `mkdocs.yml` nav.
- [x] 4.4 `docs/reference/01-domain-model.md` (ER; `sellers` genişledi, iki yeni tablo, iki bölge artık `account_id` ile bağlı), `02-api-reference.md`, `03-design-system.md` (yeni bileşen varsa).
- [x] 4.5 How-to: `docs/how-to/admin-yetkisi-verme.md` ve `how-to/index.md`. `yerel-gelistirme.md`: volume, yeni değişken, dev hesapları ve elle başvuru adımları.
- [x] 4.6 Feature dosyası `docs/features/satici-kaydi-ve-onayi.md` (Durum, Ne yapar, Bilinçli sınırlar, Açık konular, Tarihçe). `features/index.md`: tablo ve planlanan listeden çıkarma. `vitrin-ve-arama.md` Tarihçe: satıcı durumu filtresi. `hesap-ve-oturum.md` Tarihçe: satıcılık kartı ve e-posta doğrulama kapısı.
- [x] 4.7 `00-current-state.md`: L6 ve L7 kalkar; yeni sınırlamalar: antivirüs yok, volume yedeklenmiyor, admin yalnızca veritabanından; test sayıları; sıradaki adım (ilan change'i). `openspec/KALANLAR.md`: A5 (satıcı verisinin saklama süresi ve aydınlatma metni; hukuk). `AGENTS.md` §15 tablosuna A5. `_genel.md` Tarihçe.
- [x] 4.8 Delta spec'ler ana spec'lere sync edilir: yeni `specs/seller-onboarding/spec.md` (Purpose bölümüyle), `storefront-catalog` MODIFIED.
- [x] 4.9 `./docs/.tools/serve.sh --build` strict derleme temiz.
- [x] 4.10 Archive kullanıcı onayıyla yapıldı (2026-10-08). **İnsan işi, açık:** hukuk teyidi (ADR-037 açık riski, KALANLAR A5); archive'ı bloke etmedi, notlandı.

# change-deadline-sweeps-and-admin-claim-email

**Durum:** Onaylandı (2026-10-21, proje sahibi), uygulandı ve kullanıcı onayıyla archive edildi (2026-10-21).

> Bu change [`00-current-state.md`](../../../../docs/reference/00-current-state.md) L37 ve L40 sınırlamalarını kapatır.
> - L40'ı [ADR-085](../../../../docs/adr/085-defect-claim-emails.md) bilerek açık bırakmıştı: admin'e e-posta "bildirim kanalı kararı (B4) yok" diye reddedilmişti. Bugün alıcı ve satıcıya giden bütün bildirimler e-postadır ve B4'ü beklemeden eklendi ([ADR-078](../../../../docs/adr/078-shipping-notifications-email-only-three-events.md), [ADR-091](../../../../docs/adr/091-delivered-email-to-buyer-with-claim-deadline.md)). B4'ün açık sorusu SMS ve sağlayıcısıdır; bu change SMS eklemez. B4 açık kalır.
> - L37'deki risk aynı biçimde ödenmemiş sipariş işinde de var ([ADR-096](../../../../docs/adr/096-unpaid-orders-closed-by-scheduled-sweep.md)); iki iş aynı yolla düzeltilir.

## Why

**L40:** Satıcı süresinde yanıt vermezse veya alıcı talebi yönetime taşırsa talep `ESCALATED` olur ve karar admin'e kalır. Admin bunu yalnızca `/admin/claims` listesini açarak öğrenir. İki taraf da e-postayla "incelemeye alındı" bilgisini alır, ama kararı verecek kişi haber almaz. Para bekleyen bir alıcının talebi, admin listeye bakana kadar sessizce bekler.

**L37:** Talep süre işi her turda süresi dolmuş taleplerden en eskisinden başlayarak 100 tanesini alır. İadesi sağlayıcıda sürekli başarısız olan bir talep değişmeden kalır ve sonraki turda listenin başına yine gelir. Böyle 100 talep birikirse, arkadaki taleplerin hiçbiri işlenmez: satıcı süresi geçmiş açık talepler yönetime taşınmaz, reddi kabul edilmiş talepler kapanmaz. Ödenmemiş sipariş işi de aynı sırayla çalışır; sağlayıcı hatası veren 100 sipariş arkadakileri bekletir.

## What Changes

- **`api`: süre işleri hatalı kaydı atlayıp devam eder (D1):**
  - `DefectClaimSweep` ve `PendingPaymentSweep` bir turda, süresi dolmuş **bütün** kayıtları sayfa sayfa dolaşır. Sayfa boyutu mevcut ayardır (`sweep-batch-size`, `pending-payment-batch-size`).
  - Sonraki sayfa, son görülen kaydın (süre, kimlik) değerinden sonrasıyla okunur (keyset). Hata veren kayıt değişmeden kalır ama sonraki sayfayı engellemez.
  - Her kayıt bir turda en çok bir kez denenir. Hata veren kayıt sonraki turda yine denenir (bugünkü davranış).
  - Turun sonunda işlenen ve hata veren kayıt sayısı loglanır.
- **`api`: yönetime düşen talepte admin'e e-posta (D2):**
  - Talep `ESCALATED` olduğunda, hangi yoldan olursa olsun (alıcı taşıdı veya satıcının süresi doldu), `ROLE_ADMIN` rolündeki her hesaba ayrı e-posta gider. Her e-posta o hesabın dilindedir.
  - E-postada şunlar var: sipariş numarası, talebin neden yönetime düştüğü ve admin talep sayfası bağlantısı (`/admin/claims/{id}`).
  - Alıcı ve satıcının mevcut e-postaları değişmez.
  - Gönderim transaction'dan sonra yapılır; hata geçişi geri almaz (ADR-019).
  - Admin hesabı yoksa e-posta gitmez ve bu bir hata değildir.
- **`web`:** değişmez. Bağlantı mevcut admin talep detayına gider.
- **Sözleşme ve şema:** değişmez.

## Capabilities

- **Değişen:**
  - `defect-claims`: iki gereksinim değişir.
    - "Alıcı teklifi veya reddi kabul eder ya da yönetime taşır": admin e-postası eklenir.
    - "Süresi dolan talepler kendiliğinden ilerler": admin e-postası; admin hesabı olmayan durum; başarısız talebin diğerlerini bekletmediği senaryo.
  - `cart-and-checkout`: "Ödeme sağlayıcıdan doğrulanır…" gereksinimine hatalı siparişin diğerlerini bekletmediği senaryo eklenir.

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | `DefectClaimRepository.findOverdue…` ve `OrderRepository.findIdsExpiredBefore` keyset biçimine geçer (küçük projeksiyon kaydı); iki sweep sayfa döngüsü; `AccountRepository.findByRole`; `OrderNotifier.claimEscalated` admin e-postaları; `messages*.properties` (`mail.claimEscalated.adminSubject/adminBody*`); `DefectClaimSweepTest`, `DefectClaimResponseTest` ve `PendingPaymentSweepTest` genişler (`critical`) |
| `web/` | Hayır | - |
| DB | Hayır | Mevcut indeksler yeterli (bkz. design.md "Sorgu ve indeks") |
| `shared/` | Hayır | - |
| `docs/` | Evet | ADR-100, ADR-101; ADR-083, ADR-085 ve ADR-096'ya not; `ayipli-urun-ve-iade.md` ve `sepet-ve-siparis.md` Tarihçe; `00-current-state.md` (L37 ve L40 kalkar, test sayısı); `docs/how-to/admin-yetkisi-verme.md`'ye "admin talep e-postası alır" notu |

## Bağımlılık

Yeni paket yok (K7).

## Kişisel veri (K10)

Admin e-postası sipariş numarası, talep bağlantısı ve neden içerir. Alıcının adı, adresi, açıklaması ve fotoğrafı e-postaya girmez; admin bunları korumalı sayfada görür. Log'a yalnızca talep kimliği ve sayılar yazılır.

## Non-goals

- SMS veya başka kanal (B4 açık kalır).
- Admin'e diğer olaylarda e-posta: "teslim edilemedi" kararları, satıcı başvuruları, iadesi sürekli başarısız olan talepler. Her biri ayrı bir ihtiyaçtır; gerektikçe aynı yardımcıyla eklenir.
- Admin bildirim tercihleri (kapatma, özet e-posta).
- Hata veren kaydı bir süre bekletmek (geri çekilme, backoff). Gerekçe design.md D1'de.
- Gönderi durumu sorgusu: en uzun süredir sorgulanmayan gönderiden başladığı için kendiliğinden döner (ADR-075), aynı sorun yok.

## Riskler

- **Kalıcı hata maliyeti:** hata veren her kayıt her turda bir sağlayıcı çağrısı yapar. Bugün de böyledir; fark, artık arkadakilerin de işlenmesidir. Çağrı sayısı gerçek sağlayıcıyla ölçülecek (A1).
- **Çok admin:** her admin'e ayrı e-posta gider. MVP'de admin sayısı az.

# Design: change-deadline-sweeps-and-admin-claim-email

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2): D1 → ADR-100, D2 → ADR-101.
> Çerçeve:
> - [ADR-083](../../../../docs/adr/083-defect-claim-deadlines-by-scheduled-sweep.md): talep süre işi, tur başına en çok `sweep-batch-size`, hatada talep değişmez ve sonraki turda yeniden denenir.
> - [ADR-096](../../../../docs/adr/096-unpaid-orders-closed-by-scheduled-sweep.md): ödenmemiş sipariş işi, aynı desen.
> - [ADR-085](../../../../docs/adr/085-defect-claim-emails.md): talep e-postaları. Madde 3, "admin'e e-posta gitmez", bu change ile değişir; ADR-085'e ADR-101'e işaret eden bir not düşülür.
>
> ADR-083 ve ADR-096'nın kararları değişmez, bir turun nasıl dolaşıldığı netleşir; ikisine ADR-100'e işaret eden not düşülür.

## Kararlar

### D1 · Süre işleri bir turda süresi dolmuş bütün kayıtları keyset sayfalamayla dolaşır

- **Döngü:** `sweepOnce` şu adımları izler:
  1. ilk sayfayı okur: süresi dolmuş kayıtlar, (süre, kimlik) sırasıyla, sayfa boyutu kadar;
  2. her kaydı bugünkü gibi kendi transaction'ında işler. Hata o kaydı değiştirmez, loglanır ve döngü sürer;
  3. sonraki sayfayı, son okunan kaydın (süre, kimlik) değerinden **sonrası** olarak okur;
  4. sayfa boyutundan kısa bir sayfa geldiğinde durur.
- **Sorgular:** iki repository sorgusu (süre, kimlik) çiftini taşıyan küçük bir projeksiyon döner ve bir "sonrası" koşulu alır:
  - talepler: süre `coalesce(buyer_deadline_at, seller_deadline_at)` (bugünkü sıra);
  - siparişler: süre `payment_expires_at` (bugünkü sıra).
  - "Sonrası" koşulu: `süre > :afterDue or (süre = :afterDue and id > :afterId)`. İlk sayfada koşul uygulanmaz.
- **Bir kayıt bir turda en çok bir kez denenir.** Sıra katı biçimde ilerlediği için döngü sonludur. Tur sırasında başarıyla işlenen kayıt süresi dolmuş kümeden çıkar; hata veren kayıt kümede kalır ama imleç onu geride bırakır.
- **Sayfa boyutu** bugünkü ayardır (`bahceden.claims.sweep-batch-size`, `bahceden.cleanup.pending-payment-batch-size`). Anlamı "tur başına üst sınır"dan "sayfa boyutu"na döner; ad değişmez, açıklaması güncellenir.
- **Log:** tur sonunda işlenen ve hata veren kayıt sayısı tek satırda (`INFO`; hata varsa `WARN`). Kayıt içeriği yazılmaz.

**Gerekçe:**
- Sorun sıranın her turda aynı kayıtlardan başlamasıdır. İmleç, hata veren kaydı geçerek arkadakilere ulaşır. Şema değişmez ve hata veren kayıt için ayrı bir transaction gerekmez.
- Ödenmemiş sipariş sorgusu (`payment_expires_at`, `id`) kısmi indeksiyle (`016`) birebir örtüşür. Talep sorgusunun sırası bugün de indeksle örtüşmez. Talep sayısı küçüktür ve iki kısmi indeks koşulu daraltır.
- İki iş aynı yapıyı paylaşır ama ortak bir soyutlama yazılmaz. Döngü on satırdır, kayıt tipleri ve kilit yolları farklıdır (§3: genel amaçlı framework yok).

**Reddedilen alternatifler:**
- (a) **Hata veren kayda `next_attempt_at` yazıp geri çekilmek (backoff):** şema değişikliği ister ve hatayı, geri alınan transaction'ın dışında ayrı bir transaction'da yazmak gerekir. Kalıcı hatalı kayıtların sağlayıcı çağrısını azaltır, ama bu ihtiyaç gerçek sağlayıcıyla ölçülmeden bilinmez (A1).
- (b) **Turda hata veren kimlikleri dışlayıp ilk sayfayı yeniden okumak (`id not in :failed`):** liste büyüdükçe sorgu büyür; boş liste için ayrı dal gerekir.
- (c) **Sırayı "en uzun süredir denenmeyen" yapmak (gönderi sorgusu gibi):** her denemeyi yazan bir kolon ister; (a) ile aynı ek transaction sorunu.
- (d) **Sayfa boyutunu büyütmek:** sorunu erteler, çözmez.

### D2 · Talep yönetime düşünce her admin hesabına, kendi dilinde e-posta

- **Tetikleyici:** `OrderNotifier.claimEscalated`, bugün alıcı ve satıcıya e-posta gönderdiği yerde admin e-postalarını da ekler. İki çağıran var: alıcının taşıması ve satıcının süresinin dolması. Çağıran, nedeni geçişin kendisiyle verir (`DefectClaimTransition`).
- **Alıcılar:** `ROLE_ADMIN` rolündeki bütün hesaplar (`AccountRepository.findByRole`). Hesap durumu bugün tek değerlidir (`ACTIVE`); ayrı süzgeç gerekmez.
- **İçerik:**
  - sipariş numarası;
  - neden: "alıcı yönetime taşıdı" ya da "üretici süresinde yanıt vermedi";
  - admin talep sayfası bağlantısı (`/admin/claims/{id}`).
  - Alıcı adı, adresi, açıklama ve fotoğraf e-postaya girmez.
- **Gönderim:** mevcut `sendAfterCommit` ile, alıcı ve satıcı e-postalarıyla aynı listede. Hata loglanır, geçiş geri alınmaz ([ADR-019](../../../../docs/adr/019-email-via-smtp-provider-agnostic.md)).
- **Admin yoksa** e-posta listesi boş kalır; hata değildir.

**Gerekçe:**
- Kararı verecek kişi haber almıyordu. Bugün sistemin tek bildirim kanalı e-postadır ve bütün taraf bildirimleri o kanaldan gider. ADR-085'in "B4 kararı yok" gerekçesi SMS sorusuna aittir ve admin e-postasını bloklamaz.
- Admin hesapları az sayıdadır ve veritabanından verilir ([ADR-046](../../../../docs/adr/046-admin-surface-in-web-first-admin-by-sql.md)). Ayrı bir "bildirim adresi" ayarı ikinci bir kaynak olurdu.
- Neden bilgisi önceliklendirmeye yarar: süresi dolmuş talepte satıcı hiç yanıt vermemiştir.

**Reddedilen alternatifler:**
- (a) **Yapılandırmada tek bir ekip adresi (`bahceden.claims.admin-email`):** admin rolüyle ayrışabilir; rolü alınan biri e-posta almaya devam eder ya da yeni admin almaz.
- (b) **Günlük özet e-postası:** zamanlanmış ek iş; bekleyen talep sayısı ölçülmeden gerekmez (§3).
- (c) **E-postaya talep açıklamasını koymak:** alıcının serbest metni kişisel veri içerebilir. Korumalı sayfa yeterli (K10).
- (d) **Yalnızca süre dolunca bildirmek:** alıcının taşıdığı talep de aynı kararı bekler.

## Sorgu ve indeks

- Siparişler: `where status = 'PENDING_PAYMENT' and payment_expires_at < :cutoff and (payment_expires_at > :afterDue or (payment_expires_at = :afterDue and id > :afterId)) order by payment_expires_at, id`. Kısmi indeks `orders_pending_payment_expiry_idx (payment_expires_at, id)` kullanılır; yeni indeks yok.
- Talepler: mevcut koşullar + "sonrası" koşulu `coalesce(...)` üzerinde. Bugünkü sıralama da indekssizdir; değişiklik yok.
- Admin sorgusu: `account_roles` üzerinden; satır sayısı küçük.

## Yetkilendirme ve kişisel veri

- Yeni uç yok; yetkilendirme yüzeyi değişmez.
- Admin e-postası yalnızca `ROLE_ADMIN` hesaplarına gider; bağlantı korumalı admin sayfasıdır (admin olmayan 403).
- Log'a yalnızca talep kimliği, sipariş numarası ve sayılar yazılır.

## Test etkisi

- **`DefectClaimSweepTest` (`critical`):**
  - sayfa boyutu 2 iken iadesi başarısız olan iki talep, arkadaki üçüncü talebin aynı turda işlenmesini engellemez;
  - hatalı talepler `OFFERED` kalır ve sonraki turda yeniden denenir;
  - bir turda her talep en çok bir kez denenir (sağlayıcı çağrı sayısı).
- **`PendingPaymentSweepTest` (`critical`):** aynı senaryo, sağlayıcı hatası veren siparişlerle.
- **Talep e-postası testleri:**
  - süre dolunca ve alıcı taşıyınca her admin hesabına, kendi dilinde, nedeni ve bağlantıyı içeren e-posta;
  - admin olmayan hesaplara gitmez;
  - admin yoksa alıcı ve satıcı e-postaları yine gider.
- Sayfa boyutu testte küçük bir değerle verilir (`application-test.yaml` veya test özelliği).
- **Kırılması beklenen mevcut test:** alıcı/satıcı e-posta sayısını sayan escalation testleri (test kurulumunda admin hesabı varsa). Beklentiler gevşetilmez; e-postalar alıcıya göre süzülür.

## Risks / Trade-offs

- **Kalıcı hatalı kayıtlar** her turda sağlayıcıyı çağırır; bugünkü davranıştır. Gerçek sağlayıcıyla ölçülür, gerekirse (a) seçeneği ayrı change olur.
- **Uzun tur:** çok sayıda süresi dolmuş kayıt varsa tur uzar. Zamanlayıcı sabit gecikmelidir (`fixedDelay`), turlar üst üste binmez.
- **Admin e-postası gürültüsü:** her yönetime düşen talep bir e-postadır; MVP hacminde kabul.

## Migration Plan

Şema ve sözleşme değişmez. Geri alma: kod geri alınır; veri dönüşümü yok.

## Open Questions

Yok.

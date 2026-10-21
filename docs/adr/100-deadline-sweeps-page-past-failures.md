# [ADR-100] Süre işleri bir turda süresi dolmuş bütün kayıtları keyset sayfalamayla dolaşır; hata veren kayıt arkadakileri bekletmez

**Tarih:** 2026-10-21
**Durum:** Accepted
**Kaynak:** `change-deadline-sweeps-and-admin-claim-email` design.md D1

---

## Context

İki zamanlanmış iş sağlayıcıya gider ve hata alabilir:

- talep süre işi, süresi dolmuş teklifi iadeyle uygular ([ADR-083](083-defect-claim-deadlines-by-scheduled-sweep.md));
- ödenmemiş sipariş işi, sağlayıcıdan ödemeyi doğrular ([ADR-096](096-unpaid-orders-closed-by-scheduled-sweep.md)).

İkisi de hatada kaydı değiştirmez ve sonraki turda yeniden dener. İkisi de her turda en eski kayıtlardan yapılandırılmış sayıda (100) alıyordu. Kalıcı hata veren 100 kayıt birikirse, her tur aynı kayıtlarla başlar ve arkadakilere hiç sıra gelmez (`00-current-state.md` L37). Böyle olunca satıcı süresi geçmiş talepler yönetime düşmez, reddi kesinleşen talepler kapanmaz, ödenmemiş siparişler açık kalır.

## Decision

- Bir tur, süresi dolmuş **bütün** kayıtları (süre, kimlik) sırasıyla sayfalarla dolaşır:
  - ilk sayfa bugünkü sorgudur;
  - sonraki sayfa, son görülen kaydın (süre, kimlik) değerinden sonrası olarak okunur: `süre > :afterDue or (süre = :afterDue and id > :afterId)`;
  - sayfa boyutundan kısa bir sayfa gelince tur biter.
- Kayıtlar bugünkü gibi tek tek, kendi transaction'ında ve satır kilidiyle işlenir. Hata o kaydı değiştirmez ve loglanır; imleç kaydı geçer.
- Bir turda her kayıt en çok bir kez denenir. Hata veren kayıt sonraki turda yine denenir.
- Sorgular küçük bir imleç kaydı (`SweepCursor`: kimlik, süre) döner. Süreler:
  - talepte `coalesce(buyer_deadline_at, seller_deadline_at)`;
  - siparişte `payment_expires_at`. Sipariş sorgusu kısmi indeksi (`016`) kullanmaya devam eder.
- `bahceden.claims.sweep-batch-size` ve `bahceden.cleanup.pending-payment-batch-size` artık sayfa boyutudur (adları değişmez, 100). Testte 2'dir: çok sayfalı yol her testte çalışır.
- Tur sonunda hata varsa işlenen ve hata veren sayısı tek satırda `WARN` ile loglanır.
- İki iş için ortak bir soyutlama yazılmaz. Döngü kısadır; kayıt tipleri ve kilit yolları farklıdır (§3).

| Alternatif | Neden reddedildi |
|---|---|
| Hata veren kayda `next_attempt_at` yazıp geri çekilmek (backoff) | Şema değişikliği ister; hata, geri alınan transaction'ın dışında ayrı bir transaction'da yazılmalıdır. Kalıcı hatanın sağlayıcı maliyeti gerçek sağlayıcıyla ölçülmeden bilinmez (A1). |
| Hata veren kimlikleri dışlayıp ilk sayfayı yeniden okumak (`id not in`) | Liste büyüdükçe sorgu büyür; boş liste ayrı dal ister. |
| Sırayı "en uzun süredir denenmeyen" yapmak | Her denemeyi yazan bir kolon ve aynı ek transaction gerekir. |
| Sayfa boyutunu büyütmek | Sorunu erteler. |

## Consequences

### Olumlu
- Kalıcı hatalı kayıtlar, süresi dolmuş diğer kayıtların ilerlemesini durduramaz.
- Şema ve sözleşme değişmez.

### Olumsuz
- Hata veren her kayıt her turda bir kez sağlayıcıyı çağırır. Bu bugünkü davranıştır; fark, artık arkadakilerin de işlenmesidir.
- Çok sayıda süresi dolmuş kayıt varsa tur uzar. Zamanlayıcılar sabit gecikmelidir (`fixedDelay`), turlar üst üste binmez.

### Nötr / Trade-off
- Gönderi durumu sorgusu bu yolu izlemez: en uzun süredir sorgulanmayandan başladığı için zaten döner ([ADR-075](075-shipment-status-by-scheduled-polling.md)).

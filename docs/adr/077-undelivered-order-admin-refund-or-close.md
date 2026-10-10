# [ADR-077] Teslim edilemeyen sipariş: admin tam iade eder veya gerekçeyle iadesiz kapatır; stok geri eklenmez

**Tarih:** 2026-10-10
**Durum:** Accepted
**Kaynak:** `add-shipments` design.md D7; ürün sahibinin 2026-10-10 kararı

---

## Context

Kargo paketi teslim edemeyebilir: yanlış adres, alıcı yok, kargo hatası. Sebep sistemden okunamaz; taze ürünün geri dönüşte durumu belirsizdir. [ADR-056](056-cancel-before-shipment-and-defect-refund.md) anlaşmazlıkta admin hakemliğini öngörür. Ürün sahibi otomatik iade yerine admin kararını seçti.

## Decision

- Admin uçları ([ADR-046](046-admin-surface-in-web-first-admin-by-sql.md) yüzeyinde, `ROLE_ADMIN`):
  - `GET /api/v1/admin/seller-orders?status=` — `DELIVERY_FAILED` (varsayılan, karar bekleyen), `REFUNDED`, `CLOSED`; sayfalı, ortak liste şekli; başka durum 400.
  - `GET /api/v1/admin/seller-orders/{id}` — satırlar, tutarlar, iade tutarı, gönderi, alıcının adı ve telefonu (iletişim için); açık adres yok (K10).
  - `POST /api/v1/admin/seller-orders/{id}/resolution` — `{ "decision": "REFUND" | "CLOSE", "reason": "…" }`.
- `REFUND`: satıcı siparişi kilitlenir, `REFUNDED` olur; ürün tutarı + kargo ücreti satıcının alt üye payından iade edilir (`refunds.reason = UNDELIVERED`); komisyon ve hakediş geçersiz olur; **stok geri eklenmez**. Gerekçe isteğe bağlıdır. İade başarısızsa 502 ve geri alma.
- `CLOSE`: gerekçe zorunlu (10–500 karakter); para hareketi yok, hakediş geçerli kalır; `CLOSED`.
- Karar bekleyen değilse 409 `ORDER_INVALID_TRANSITION`; ödenmemiş satıcı siparişi 404. Karar ve gerekçe geçmişe admin hesabıyla yazılır; alıcıya ve satıcıya hesap dillerinde e-posta gider.
- `web`: `/admin/orders` liste ve detay; iki eylem de onay penceresiyle (iade açıklaması isteğe bağlı, kapatma gerekçesi zorunlu).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Otomatik tam iade | Ürün sahibi reddetti; satıcı alıcı hatasının bedelini öder. |
| Kısmi iade seçeneği | Ayıplı ürün change'inin konusu (ADR-056 §5); burada iki uç karar yeterli. |
| Satıcıya karar yetkisi | Çıkar çatışması. |
| İadede stoğu geri eklemek | Dönen taze ürün satılabilir sayılmaz; satıcı isterse stoğu kendisi artırır. |

## Consequences

### Olumlu
- Her teslim edilemeyen sipariş izli, gerekçeli ve iki tarafa bildirilmiş bir kararla kapanır.
- Admin geçmiş kararları sekmelerde izler.

### Olumsuz
- Admin için yeni bir iş yükü ve süre baskısı; karar süresi için hedef veya hatırlatma yok.
- İadesiz kapatmanın tüketici hukukuyla uyumu hukuk teyidine açıktır (KALANLAR A5).

### Nötr / Trade-off
- Kararın gerekçesi yalnızca geçmiş kaydında ve e-postada durur; admin detayında gösterilmez.

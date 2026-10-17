# [ADR-083] Talep süreleri tek bir Spring zamanlama işinde; her talep kendi satır kilidiyle işlenir; alıcının sessizliği teklifi kabul, reddi kesinleşmiş sayar

**Tarih:** 2026-10-13
**Durum:** Accepted
**Kaynak:** `add-defect-claims` design.md D5; ürün sahibinin 2026-10-13 kararları (satıcı süresi 48 saat; alıcı 48 saat içinde yanıt vermezse kabul)

---

## Context

[ADR-056](056-cancel-before-shipment-and-defect-refund.md) §4 satıcı süresinde yanıt vermezse admin'in karar vereceğini söyler. Ürün sahibi satıcının ve alıcının yanıt süresini 48 saat olarak belirledi. Alıcı süresinde yanıt vermezse satıcının teklifi kabul edilmiş, reddi kesinleşmiş sayılır. Bu, para hareketini bir kullanıcı isteği olmadan yapmayı gerektirir.

Gönderi durumu için Spring zamanlayıcısı zaten kurulu ([ADR-075](075-shipment-status-by-scheduled-polling.md)).

## Decision

1. **Zamanlama.** `DefectClaimSweep`, `bahceden.claims.sweep-interval` (`PT5M`) aralıkla çalışır.
   - `bahceden.claims.sweep-enabled` kapalıyken (testler) zamanlayıcı kurulmaz; testler işi sabit saatle doğrudan çağırır.
   - Desen gönderi sorgusunun desenidir.
2. **İşleme.** Süresi geçmiş talepler indeksli sorguyla ve en çok `sweep-batch-size` (100) kadar okunur. Her talep ayrı bir transaction'da `SELECT … FOR UPDATE` ile kilitlenir ve durumu ile süresi yeniden denetlenir:

   | Durum | Süre geçince |
   |---|---|
   | `OPEN` (satıcı süresi) | `ESCALATED` |
   | `OFFERED` (alıcı süresi) | teklifteki adetlerle iade, `REFUNDED`. Sağlayıcı hatasında talep `OFFERED` kalır, `WARN` loglanır ve sonraki çalışmada yeniden denenir |
   | `REJECTED` (alıcı süresi) | `CLOSED` |

3. **Eylemci.** Süre işinin geçişlerinde eylemci yoktur; `resolved_by = DEADLINE` yazılır.
4. **Eşzamanlılık.** Kullanıcının eylemi ile süre işi aynı talebi aynı anda işlerse satır kilidi ikisini sıraya koyar; ikincisi değişmiş durumu görüp çıkar. İade iki kez yapılmaz ve bu `critical` testle sabitlenir.
5. **Süreler yapılandırmadadır:** `bahceden.claims.window`, `seller-response`, `buyer-response` (hepsi `PT48H`). Açılışta doğrulanır.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Süreyi okuma anında türetmek | Para hareketi okuma anında yapılamaz. |
| Alıcı sessiz kalırsa süresiz beklemek | Satıcının hakedişi süresiz belirsiz kalır; ürün sahibi kabulü seçti. |
| Alıcı sessiz kalırsa admin'e düşürmek | Admin yükü artar; çoğu teklif tartışmasızdır. |
| Talep başına bellekte zamanlanmış görev | Yeniden başlatmada kaybolur. |
| Kuyruk veya iş zamanlayıcı kütüphanesi | `AGENTS.md` §3 ve K7. |

## Consequences

### Olumlu
- Hiçbir talep sahipsiz kalmaz; her talep en geç 96 saat sonra uç duruma ya da admin'e ulaşır.
- Gönderi sorgusuyla aynı desen; ek altyapı yok.

### Olumsuz
- Zamanlanmış iş para hareket ettirir; hata payı yoktur. Kilit ve yeniden denetim bu yüzden zorunludur.
- Durum en çok bir aralık (5 dakika) kadar gecikir.
- Zamanlayıcı her örnekte çalışır (L33 ile aynı sınır); satır kilidi çift işlemeyi önler.

### Nötr / Trade-off
- Süre işinin geçişleri e-posta gönderir; e-postalar çalışma sonunda değil, her talebin transaction'ından sonra gider.

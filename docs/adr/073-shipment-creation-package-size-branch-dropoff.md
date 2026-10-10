# [ADR-073] Gönderi açma: koli boyutu seçilir, gönderici satıcının adı, ili ve telefonu, paket şubeye teslim edilir; istek tekrarlanabilir

**Tarih:** 2026-10-10
**Durum:** Accepted
**Kaynak:** `add-shipments` design.md D3; ürün sahibinin 2026-10-10 kararı (şubeye teslim, satıcıdan açık adres alınmaz)

---

## Context

Entegratörler gönderi açarken gönderici, alıcı ve paket bilgisi ister. Satıcı kaydında bugün yalnızca görünen ad, il ve telefon var; açık adres yok. Satıcılar sahada telefonla çalışır (§12.4) ve kargo bildirimi en kısa yol olmalıdır (§12.5). Aynı düğmeye iki kez basılması iki gönderi açmamalıdır.

## Decision

- Uç: `POST /api/v1/sellers/me/orders/{id}/shipment`, gövde `{ "packageSize": "SMALL" | "MEDIUM" | "LARGE" }`.
- Tek transaction: onaylı satıcı kapısı ([ADR-044](044-seller-role-follows-status-with-query-gate.md)); satıcı siparişi sahiplik sorgusuyla **kilitli** okunur ([ADR-007](007-query-level-ownership-scoping.md)); etkin gönderi varsa o döner (200); durum `NEW` değilse 409 `ORDER_INVALID_TRANSITION`; satıcının telefonu yoksa 422 `SHIPMENT_SENDER_PHONE_MISSING` ve entegratör çağrılmaz; aksi halde entegratörde gönderi açılır, etiket alınıp depolanır, gönderi `CREATED` kaydedilir (201).
- **Gönderici:** satıcının görünen adı, ili ve telefonu; paket şubeye teslim edilir. **Alıcı:** siparişe kopyalanmış teslimat adresi (ad, telefon, il, ilçe, açık adres, posta kodu); dış servise iletilmesi ADR-057 §4 ile onaylıdır.
- **Koli boyutu** üç seçenektir; desi karşılığı yapılandırmadadır (varsayılan 5 / 15 / 30) ve yalnızca entegratöre bildirilir, kargo ücretini etkilemez ([ADR-058](058-flat-shipping-fee-per-seller-and-declared-handover.md)).
- Entegratör referansı `BHC-<satıcıSiparişiId>-<n>`'dir; `n` o satıcı siparişinin gönderi sayısı + 1 (iptal edilip yeniden açılan gönderi yeni referans alır; gerçek entegratörlerin tekrar korumasına uygun).
- Entegratör hatası 502 `SHIPPING_INTEGRATOR_ERROR`; hiçbir şey kaydedilmez. Log'a takip numarası ve adres yazılmaz (K10).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Satıcının açık adresi ve kapıdan alım | Ürün sahibi reddetti (2026-10-10): satıcıdan yeni kişisel veri toplanmaz. |
| Serbest desi alanı | Hata payı yüksek; ücreti etkilemediği için hassasiyet gereksiz. |
| Gönderiyi ödeme anında otomatik açmak | Ürün hazırlanmadan etiket oluşur; iptal edilen siparişlerde boş gönderi birikir. |
| Kilit yerine yalnızca benzersiz indeks | Çakışan ikinci istek 500'e düşer ve entegratörde ikinci gönderi açılmış olur; kilit isteği sıraya sokar, indeks ikinci savunma hattıdır. |

## Consequences

### Olumlu
- Satıcı tek dokunuşla gönderi açar; tekrar basmak güvenlidir.
- Satıcıdan yeni kişisel veri istenmez.

### Olumsuz
- Telefonu olmayan satıcı kaydı (eski seed kayıtları) gönderi açamaz; hata mesajı başvuru bilgisini güncellemeyi söyler.
- Entegratör çağrısı satır kilidi tutulurken yapılır; yavaş entegratör aynı siparişe gelen ikinci isteği bekletir.
- Entegratörde açılıp veritabanına yazılamayan gönderi yetim kalabilir; gerçek entegratör uygulaması referansla temizlik yapmalıdır (KALANLAR A2).

### Nötr / Trade-off
- Bazı entegratörler şubeye teslimi kabul etmeyebilir; seçimde bu koşul aranır.

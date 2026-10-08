# [ADR-057] Gönderi kargo entegratörü API'si ile oluşturulur; entegratör bir arayüzün arkasında, seçimi sonraya kalır

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `openspec/KALANLAR.md` A2 (kargo firması, entegrasyon biçimi); ürün sahibinin 2026-10-08 kararı

---

## Context

Satıcı için kargo bildirimi, ürün eklemeyle birlikte en kritik akıştır (`AGENTS.md` §12.5). Üç yol vardı:

- Satıcı kendi anlaştığı firmayla gönderir ve takip numarasını elle girer.
- Pazaryeri tek bir kargo firmasıyla anlaşır ve onun API'sini kullanır.
- Pazaryeri bir **kargo entegratörüyle** anlaşır: tek API ile birden çok firmaya (Aras, Yurtiçi, MNG ve benzerleri) gönderi açılır, etiket ve barkod üretilir, durum takip edilir.

Ürün sahibi entegratörü seçti. Entegratör seçimi ve sözleşmesi ise ticari görüşmeye kaldı. Kargo maliyeti pazaryerine faturalanır ([ADR-058](058-flat-shipping-fee-per-seller-and-declared-handover.md)).

Ödeme tarafında aynı durum [ADR-054](054-split-payment-via-provider-port-provider-pending.md) ile bir arayüz ve `fake` uygulamayla çözüldü. Geliştirme ve testte gerçek gönderi açmak `AGENTS.md` K10 gereği yasaktır.

## Decision

1. **Gönderi sistemden açılır.** Satıcı siparişi hazırladığında panelden "kargoya hazırla" der; `api` entegratörde gönderi oluşturur, etiketi (barkod) alır ve satıcıya yazdırılabilir olarak sunar. Satıcı takip numarası yazmaz.
2. **Gönderi durumu entegratörden gelir** (oluşturuldu, kargoya verildi, yolda, teslim edildi, teslim edilemedi). Teslim edildi bilgisi ayıplı ürün süresini başlatır ([ADR-056](056-cancel-before-shipment-and-defect-refund.md)). Alma yöntemi (bildirim veya periyodik sorgu) entegratör seçilince belirlenir.
3. **Entegratör `shipping` domaininde tek bir arayüzün arkasındadır:** gönderi oluştur, etiket al, durum sorgula, gönderi iptal et. Geliştirme ve testte `fake` uygulama çalışır. Gerçek uygulama sözleşme imzalanınca eklenir; Java HTTP istemcisiyle, SDK'sız (K7).
4. **Gönderinin adres bilgisi** (alıcı adı, adresi, telefonu) entegratöre iletilir. Bu, dış servise kişisel veri gönderimidir; bu kararla onaylanmıştır (§9). Takip numarası ve adres uygulama log'una yazılmaz (K10).
5. **Entegratör seçimi açık kalır** (KALANLAR A2'de).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Satıcı takip numarasını elle girer | Entegrasyonsuz ve en hızlı başlangıç. Ama ücret pazaryerinin sabit ücretiyle alındığı için maliyet kontrolü pazaryerinde olmalı; elle girilen numarada durum takibi ve teslim tarihi de elle kalır. Ürün sahibi tercih etmedi. |
| Tek kargo firmasının API'si | Tek firmaya bağımlılık; firmanın kapsamadığı bölgede gönderi açılamaz. |
| Birden fazla entegratörü aynı anda desteklemek | §3 ve §15: tek gerçek uygulama. |
| Entegratörü şimdi seçmek | Fiyatlar teklif usulüyle gelir; ürün sahibi seçimi ticari görüşmeye bıraktı. |

## Consequences

### Olumlu
- Satıcı numara yazmaz, etiketi yazdırıp paketi verir; hata payı düşer.
- Teslim tarihi sistemde güvenilir biçimde oluşur; iade süresi buna bağlanabilir.
- Kargo maliyeti ve firma seçimi pazaryerinin kontrolünde.

### Olumsuz
- Satıcının yazıcıya (veya etiket çıktısı alabileceği bir yere) ihtiyacı olur; sahada telefonla çalışan satıcı için etiket akışı ayrıca tasarlanmalı.
- Alıcının adresi ve telefonu üçüncü tarafa gider; aydınlatma metni gerekir (A5).
- Gerçek entegratör bağlanana kadar canlıda gönderi açılamaz.

### Nötr / Trade-off
- Gönderi durumunu alma yöntemi (webhook ya da zamanlanmış sorgu) entegratör seçimine bağlı; webhook seçilirse imza doğrulaması gerekir.

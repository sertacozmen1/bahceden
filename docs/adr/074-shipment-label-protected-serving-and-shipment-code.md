# [ADR-074] Etiket depolamada, yalnızca sahibine korumalı başlıklarla sunulur; panelde büyük puntolu gönderi kodu; sahte etiket fontsuz PDF

**Tarih:** 2026-10-10
**Durum:** Accepted
**Kaynak:** `add-shipments` design.md D4

---

## Context

Kargo etiketi alıcının adını, telefonunu ve adresini taşır (K10). Satıcı belgesi için aynı sorun [ADR-045](045-seller-documents-table-content-sniffing-protected-serving.md) ile çözüldü: içerikten tür tespiti, `nosniff`, `CSP: sandbox`, `no-store`. Dosyalar yerel volume'dadır ([ADR-039](039-uploads-on-local-volume-served-by-api.md)). Satıcıların çoğu sahada telefonla çalışır, yazıcısı olmayabilir. `api` çalışma imajı Alpine tabanlıdır ve yazı tipi dosyası içermez.

## Decision

- Etiket entegratörden alındığı anda `FileStorage` ile yazılır; türü içerikten tespit edilir ve yalnızca PDF veya PNG kabul edilir, başka tür entegratör hatası sayılır (502). Her istekte entegratöre gidilmez.
- `GET /api/v1/sellers/me/orders/{id}/shipment/label` etiketi yalnızca satıcı siparişinin sahibine (sorgu seviyesinde) döner; başkasınınki 404. Başlıklar ADR-045 ile aynıdır ve tek yerde toplanır (`ProtectedFileResponses`); satıcı belgesi de artık bu yardımcıyı kullanır.
- `web`, etiketi `/api/seller/orders/[id]/label` Route Handler'ından akış olarak aktarır (belge aktarma deseni). Panelde "Etiketi aç ve yazdır" bağlantısı etiketi yeni sekmede açar; sayfa içi önizleme yoktur.
- Takip numarası panelde **gönderi kodu** olarak büyük puntoyla ve "Kodu kopyala" düğmesiyle gösterilir: yazıcısı olmayan satıcı kodu şubede söyler.
- Sahte etiket, yazı tipi dosyası gerektirmeyen elle üretilmiş tek sayfalık bir PDF'tir (PDF'in yerleşik Helvetica'sı, yalnızca ASCII); kişisel veri ve barkod taşımaz.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Etiketi veritabanında `bytea` saklamak | Dosyalar için karar ADR-039'da verildi. |
| Etiketi her istekte entegratörden çekmek | Entegratöre bağımlılık ve gecikme; bazı entegratörler etiketi bir kez verir. |
| Etiketi sayfa içinde `<iframe>` ile önizlemek | `CSP: sandbox` tarayıcının PDF görüntüleyicisini çerçevede engeller; başlığı gevşetmek ADR-045'in korumasını bozar. |
| Sahte etiketi Java2D ile PNG olarak çizmek | Alpine imajında yazı tipi yok, metin çizimi hata verir; imaja yazı tipi paketi eklemek bağımlılık kararıdır (K7). |
| PDF kütüphanesi | Sahte etiket için değeri yok (K7). |

## Consequences

### Olumlu
- Etiketteki kişisel veri belge ile aynı korumayla sunulur; koruma tek yerde.
- Yazıcısı olmayan satıcı akışı tamamlayabilir.

### Olumsuz
- Etiketler silinmez; saklama süresi hukuk kararı bekler (KALANLAR A5).
- Sayfa içi önizleme yok; satıcı yeni sekmeye geçer.

### Nötr / Trade-off
- Şubede kodla kabul entegratöre ve firmaya bağlıdır; gerçek entegratör seçildiğinde panel metni uyarlanır.

# [ADR-093] Sipariş oluşturma için e-posta doğrulaması zorunludur: `422 ORDER_EMAIL_NOT_VERIFIED`

**Tarih:** 2026-10-19
**Durum:** Accepted
**Kaynak:** `add-checkout-email-verification` design.md D1

---

## Context

Hesap doğrudan etkin açılır ve e-posta doğrulaması girişi engellemez ([ADR-017](017-email-verification-non-blocking-and-2fa-switch.md)); bu, `00-current-state.md` L11'de kabul edilmiş bir borçtu. Satıcı başvurusu kapıyla çözüldü ([ADR-043](043-seller-application-requires-verified-email.md)). Sipariş dilimi kapı koymamış, karar proje sahibine bırakılmıştı.

Sipariş, kargo, teslim ve ayıplı ürün talebi e-postaları alıcının adresine gider. Teslim e-postası 48 saatlik bir hakkın son anını taşır ([ADR-091](091-delivered-email-to-buyer-with-claim-deadline.md)). Proje sahibi 2026-10-17'de karar verdi: doğrulanmamış alıcı sipariş veremez.

Doğrulama durumu JWT'de taşınmaz; `GET /accounts/me` onu güncel kayıttan okur ([ADR-024](024-roles-in-jwt-claims-fresh-me-endpoint.md)).

## Decision

- `POST /api/v1/orders`, hesabın e-postası doğrulanmamışsa **422** `ORDER_EMAIL_NOT_VERIFIED` döner; sipariş oluşmaz, ödeme başlamaz.
- Sıra:
  1. tekrar anahtarının biçimi;
  2. aynı anahtarla oluşmuş sipariş varsa o döner (kapıdan **önce**; oluşmuş sipariş geri alınamaz);
  3. doğrulama kapısı;
  4. adres, sepet ve ödeme.
- Doğrulama durumu `AccountRepository`'den güncel okunur. Doğrulayan alıcı aynı erişim token'ıyla hemen sipariş verebilir.
- Giriş, sepet, adres defteri, ödemenin tamamlanması ve sipariş görüntüleme kapısızdır.

| Alternatif | Neden reddedildi |
|---|---|
| Girişte kapı | ADR-017'ye aykırı; alıcı sepetini ve siparişlerini göremez olur. |
| Sepete eklemede kapı | Vitrin akışını erken keser; niyet kaybolur. |
| Ödeme tamamlamada kapı | Kapıdan önce açılmış siparişler ödenemez kalır; sağlayıcıda para çekilmiş olabilir. |
| `SELLER_EMAIL_NOT_VERIFIED`'ı yeniden kullanmak | Ad yanlış alanı söyler; genel bir koda geçmek sözleşmede kırıcı değişiklik (`AGENTS.md` §9). |
| **403** | Yetki eksikliği değil; kullanıcının giderebileceği bir iş kuralı. Projede bu durum 422'dir. |

## Consequences

### Olumlu
- Sipariş haberleri, para ödenmeden önce teslim edilebildiği kanıtlanmış bir adrese gider.
- L11 borcu iki ticari eylemde de (başvuru ve sipariş) kapandı.

### Olumsuz
- Yeni alıcı ilk siparişinden önce gelen kutusuna gitmek zorunda.
- Doğrulama e-postası gecikir ya da spam klasörüne düşerse alıcı siparişi tamamlayamaz.

### Nötr / Trade-off
- Doğrulama sayfası alıcıyı ödemeye geri götürmez; alıcı ödeme sekmesini yeniler.

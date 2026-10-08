# [ADR-060] Satıcı ödeme hesabı ayrı tabloda; kimlik numarası ve IBAN sunucuda doğrulanır, yanıtta maskelenir; alt üye kaydı eşzamanlıdır

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-cart-and-checkout` design.md D2

---

## Context

[ADR-054](054-split-payment-via-provider-port-provider-pending.md) bölünmüş ödemeyi seçti: satıcı ödeme sağlayıcısında alt üye işyeri olmalı. [ADR-037](037-seller-application-with-document-and-manual-approval.md) §4 başvuruda IBAN ve kimlik numarası almıyor; bu bilgiler ayrı bir adımda gelmeliydi. IBAN ve TCKN kişisel veridir (K10).

## Decision

- **Tablo `seller_payout_accounts`**, satıcı başına bir satır: `legal_type` (`INDIVIDUAL`, `SOLE_PROPRIETOR`, `COMPANY`), `legal_name`, `tax_id`, `tax_office`, `iban`, `status` (`ACTIVE`, `PROVIDER_REJECTED`), `provider_sub_merchant_key`.
- **Doğrulama** (saf Java, `critical` testli): bireysel ve şahıs şirketi için TCKN (11 hane, iki sağlama hanesi); şirket için VKN (10 hane, yalnızca biçim — sağlama algoritması resmi değil); şahıs şirketi ve şirkette vergi dairesi zorunlu; IBAN `TR` + 24 hane, mod-97, boşluklar kabul edilir.
- `PUT /api/v1/sellers/me/payout-account` kaydı oluşturur veya günceller ve **aynı istekte** sağlayıcıda alt üye kaydını açar ya da günceller. Sağlayıcı reddederse kayıt `PROVIDER_REJECTED` olarak **saklanır** ve istek 422 `PAYOUT_ACCOUNT_REJECTED` döner (transaction bu hata için geri alınmaz). Sağlayıcıya ulaşılamazsa 502, hiçbir şey kaydedilmez. Uç onaylı satıcı kapısından geçer ([ADR-044](044-seller-role-follows-status-with-query-gate.md)).
- **Maskeleme:** yanıt IBAN'ı `TR** **** **** **** **** **12 34`, kimlik numarasını `*******1234` biçiminde döner. Tam değer hiçbir uçtan okunmaz ve log'a yazılmaz; satıcı değiştirmek için iki değeri her kayıtta yeniden tam girer.
- **Saklama:** değerler veritabanında düz metindir; uygulama düzeyi şifreleme yoktur (bkz. olumsuz sonuçlar).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Ödeme bilgisini başvuruya eklemek | ADR-037 §4'e aykırı; onaylanmamış satıcının IBAN'ını tutmak gereksiz veri toplamaktır. |
| Uygulama düzeyinde AES şifreleme | Ayrı anahtar ve rotasyon gerekir; anahtar tek sunucuda veritabanıyla yan yana durur, kazanım sınırlı. KALANLAR B5 ile birlikte değerlendirilir. |
| Asenkron alt üye kaydı | Kuyruk veya zamanlanmış iş gerekir (§3); sağlayıcıların kayıt API'si eşzamanlı. |
| Reddi 422 ile geri alıp kaydetmemek | Satıcının girdiği bilgiler kaybolur ve "satışa kapalı" durumu görünmez. |

## Consequences

### Olumlu
- Satıcı bilgilerini bir kez girer; sağlama hataları anında ve ilgili alanda görünür.
- Tam değer yalnızca kayıt anında ağdan geçer; ekranda ve log'da yoktur.

### Olumsuz
- IBAN ve TCKN veritabanında şifresiz; disk ve yedek şifrelemesi B5'e bağlı (`00-current-state.md` bilinen sınırlamalar).
- Satıcı her güncellemede iki numarayı yeniden yazmak zorundadır.
- Sağlayıcıya kişisel veri gider (ADR-054 ile onaylı); aydınlatma metni A5'e bağlı.

### Nötr / Trade-off
- Bu change'te sağlayıcı `fake`'tir; dışarı veri gitmez.

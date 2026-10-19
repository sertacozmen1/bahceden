# [ADR-094] Ödeme sayfası doğrulanmamış alıcıya form yerine doğrulama paneli gösterir

**Tarih:** 2026-10-19
**Durum:** Accepted
**Kaynak:** `add-checkout-email-verification` design.md D2

---

## Context

[ADR-093](093-order-requires-verified-email.md) sipariş oluşturmayı doğrulanmış e-postaya bağlar. Alıcının bunu adres seçip "Ödemeye geç"e bastıktan sonra öğrenmesi boşa iştir.

Satıcı başvurusu sayfası aynı kapıyı form yerine doğrulama yönlendirmesi ve mevcut "yeni doğrulama bağlantısı gönder" düğmesiyle anlatıyor ([ADR-043](043-seller-application-requires-verified-email.md), [ADR-026](026-verification-link-resend-requires-session.md)). Ödeme sayfası oturum sahibinin hesabını zaten okuyor.

## Decision

- `/checkout`, `account.emailVerified` yanlışsa sağ kartta ödeme formu yerine şunları gösterir:
  - "Önce e-postanızı doğrulayın" başlığı;
  - sipariş haberlerinin e-postayla gideceğini, spam klasörüne bakılmasını ve doğruladıktan sonra sayfanın yenilenmesini söyleyen kısa metin;
  - mevcut `ResendVerificationButton`.
- Sipariş özeti, toplamlar ve cayma bilgisi görünmeye devam eder.
- Sepet sayfası değişmez. Kapı tek yerde anlatılır.
- Sunucu yine de 422 dönerse mevcut hata eşlemesi mesajı formun üstünde gösterir; otorite `api`'dir.

| Alternatif | Neden reddedildi |
|---|---|
| Formu gösterip sunucu hatasını beklemek | Alıcı adres seçip düğmeye bastıktan sonra öğrenir. |
| Sepette "Siparişe geç"i kapatmak | Nedeni anlatacak yer dar; devre dışı düğme anlaşılırlığı ve erişilebilirliği zayıflatır. |
| Ayrı `/checkout/verify` sayfası | Yeni rota ve yönlendirme; panel yeterli. |

## Consequences

### Olumlu
- Alıcı ne ödeyeceğini görürken bir sonraki adımı da görür.
- Başvuru ve ödeme aynı kalıbı kullanır.

### Olumsuz
- Doğruladıktan sonra alıcı sayfayı kendisi yeniler; otomatik yenileme yok.

### Nötr / Trade-off
- Panel istemci tarafında bir kolaylıktır; kapı `api`'dedir.

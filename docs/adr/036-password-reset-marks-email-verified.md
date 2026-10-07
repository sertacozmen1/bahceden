# [ADR-036] Sıfırlama bağlantısını kullanmak e-postayı doğrular

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-password-reset` design.md D4

---

## Context

`email_verified` bir bilgidir, girişi engellemez ([ADR-017](017-email-verification-non-blocking-and-2fa-switch.md)). Bu bilgi bugün yalnızca doğrulama bağlantısıyla işaretleniyor. O bağlantının kanıtladığı tek şey, kullanıcının adresin gelen kutusuna erişimi olmasıdır.

Sıfırlama bağlantısı da yalnızca hesabın kayıtlı adresine gider ([ADR-034](034-password-reset-request-reveals-nothing.md)).

## Decision

Sıfırlama başarılı olduğunda hesap henüz doğrulanmamışsa `email_verified` işaretlenir. İşlem idempotenttir: zaten doğrulanmış hesapta bir şey değişmez.

| Alternatif | Neden reddedildi |
|---|---|
| Doğrulama durumuna dokunmamak | Aynı kanıt (gelen kutusuna erişim) iki farklı sonuç üretir ve kullanıcıdan aynı şeyi ikinci bir e-postayla tekrar kanıtlaması istenir. |
| Sıfırlama talebinde doğrulamak | Talep yalnızca adresi alır; gelen kutusuna erişim henüz kanıtlanmamıştır. |

## Consequences

### Olumlu
- Doğrulama bağlantısı ulaşmamış ama sıfırlama bağlantısını kullanmış kullanıcı ek adım atmaz.

### Olumsuz
- E-posta doğrulamanın iki yolu var. İleride doğrulamaya bağlı bir kapı (örneğin sipariş, L11) eklenirse iki yolun da aynı güveni verdiği hatırlanmalı.

### Nötr / Trade-off
- Doğrulama token'ları bu işlemde tüketilmez; kullanılırlarsa zaten doğrulanmış hesabı tekrar işaretlerler, zararsızdır.

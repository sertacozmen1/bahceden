# [ADR-043] Satıcı başvurusu için e-posta doğrulaması zorunludur

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-seller-onboarding` design.md D4

---

## Context

[ADR-017](017-email-verification-non-blocking-and-2fa-switch.md) e-posta doğrulamasını girişin ön koşulu olmaktan çıkardı ve bunu bilinçli bir borç olarak kabul etti (`00-current-state.md` L11). Bu kapının ilk ticari akışta değerlendirilmesi gerekiyordu.

Satıcı başvurusunun admin kararı e-postayla bildirilir ([ADR-037](037-seller-application-with-document-and-manual-approval.md)). Doğrulanmamış bir adrese giden karar kaybolabilir.

## Decision

- **E-postası doğrulanmamış hesap başvuramaz:** `422 SELLER_EMAIL_NOT_VERIFIED`.
- Arayüz form yerine doğrulama yönlendirmesi ve mevcut "yeni doğrulama bağlantısı" eylemini gösterir ([ADR-026](026-verification-link-resend-requires-session.md)).
- Giriş yine engellenmez; ADR-017'ye aykırı bir şey yoktur. Kapı yalnızca başvuruya konur.

| Alternatif | Neden reddedildi |
|---|---|
| Doğrulamayı onay anında admin'e bırakmak | Admin'e mekanik bir kontrol yükler; kararın bildirimi yine kaybolabilir. |
| Kapı koymamak | Bildirimin teslim edileceğine dair hiçbir güvence kalmaz. |

## Consequences

### Olumlu
- Karar e-postası doğrulanmış bir adrese gider.
- L11 borcu, para ve yasal sorumluluk taşıyan ilk eylemde kapandı.

### Olumsuz
- Doğrulamayı atlamış bir kullanıcı başvurmadan önce bir adım daha atar.

### Nötr / Trade-off
- Sipariş dilimi aynı kapıyı ayrıca değerlendirecek; bu karar ona emsal olur ama onu bağlamaz.

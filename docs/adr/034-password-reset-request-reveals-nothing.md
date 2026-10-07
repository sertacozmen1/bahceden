# [ADR-034] Parola sıfırlama talebi hesabı açığa çıkarmaz; kayıtsız adrese e-posta gitmez

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-password-reset` design.md D2

---

## Context

Parola sıfırlama talebi oturumsuzdur ve bir e-posta adresi alır. Bu yüzden "bu adres kayıtlı mı?" sorusunu sormanın en kolay yollarından biridir.

Kayıt ucu aynı soruyu her durumda aynı yanıtı vererek kapatmıştı ([ADR-021](021-identity-responses-do-not-reveal-accounts.md)). Kayıtlı adrese "hesabınız zaten var" e-postası gönderiliyordu.

## Decision

- `POST /api/v1/accounts/password-resets` her durumda **202** ve aynı gövdeyi döner (`PasswordResetRequested`, isteğin dilinde mesaj).
- **Hesap varsa:** sıfırlama bağlantısı hesabın dilinde gönderilir ([ADR-032](032-account-locale-stored-and-refreshed-on-login.md)).
- **Hesap yoksa:** hiçbir şey yapılmaz ve **e-posta gönderilmez**.
- Hız sınırı iki anahtarla uygulanır ([ADR-022](022-in-memory-fixed-window-rate-limiting.md)):
  - **istemci başına** saatte 10 talep;
  - **e-posta başına** saatte 3 talep.

  İkisinden biri aşılırsa `429 RATE_LIMITED` döner ve e-posta gönderilmez.
- Log'a e-posta maskeli yazılır, token hiç yazılmaz (K10).

| Alternatif | Neden reddedildi |
|---|---|
| Kayıtsız adreste `404` | Hesap keşfi. |
| Kayıtsız adrese "böyle bir hesap yok" e-postası (kayıt ucundaki gibi) | Ucu rastgele adreslere istenmeyen posta gönderen bir araca çevirir. Kayıt ucunda bilgilendirilmesi gereken bir hesap sahibi vardı; burada yok. |
| Yalnızca istemci başına sınır | Kurbanın gelen kutusu dağıtık kaynaklardan doldurulabilir. |
| Yalnızca e-posta başına sınır | Tek kaynaktan çok sayıda adres taranabilir. |

## Consequences

### Olumlu
- Yanıt gövdesi ve durum kodu hesabın varlığını söylemez.
- Sistem üçüncü kişilere istenmeyen e-posta göndermek için kullanılamaz.

### Olumsuz
- Kayıtlı adres için token yazılıp e-posta gönderilir, kayıtsız adres için hiçbir şey yapılmaz. Yanıt süresi farkından hesap keşfi kuramsal olarak mümkündür. Aynı yüzey kayıt ucunda da var; hız sınırı daraltır. Kalıcı çözüm asenkron gönderimdir, ama kuyruk bugün `AGENTS.md` §3'e aykırı.
- Adresini yanlış yazan kullanıcıya bunu söyleyemiyoruz; yalnızca "kayıtlı bir hesap varsa" diyebiliyoruz.

### Nötr / Trade-off
- Hız sınırı bellekte ve tek örnek içindir; ölçek değişirse [ADR-022](022-in-memory-fixed-window-rate-limiting.md) ile birlikte yeniden ele alınır.

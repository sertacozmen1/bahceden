# [ADR-085] Talep e-postaları: her durum değişikliğinde karşı tarafa, yönetime taşınınca ve sonuçta iki tarafa

**Tarih:** 2026-10-13
**Durum:** Accepted
**Kaynak:** `add-defect-claims` design.md D7

---

## Context

Talepteki her süre karşı tarafın bir eylemiyle başlar (ADR-083). Taraf haberdar olmazsa süre sessizce dolar ve sonuç doğurur. Bugün kurulu tek kanal e-postadır; SMS kararı açıktır (KALANLAR B4). E-postalar hesabın dilinde ve transaction sonrasında gider ([ADR-032](032-account-locale-stored-and-refreshed-on-login.md), `OrderNotifier` deseni).

## Decision

1. **Gönderilecek e-postalar:**

   | Olay | Alıcıya | Satıcıya |
   |---|---|---|
   | Talep açıldı | — | satırlar, adetler ve son tarih |
   | Satıcı teklif verdi veya reddetti | teklif ya da gerekçe ve alıcının son tarihi | — |
   | Yönetime taşındı (alıcı veya süre) | e-posta | e-posta |
   | Sonuçlandı (`REFUNDED` veya `CLOSED`) | iade tutarı ya da gerekçe | iade tutarı ve yeni hakediş ya da kapanış |

2. **Sonuç e-postaları:** satıcının kabulü doğrudan sonuçtur ve yalnızca "sonuçlandı" e-postası gider. Süre işinin geçişleri de aynı e-postaları gönderir.
3. **Kapsam dışı:** admin'e e-posta gitmez; admin listeyi izler.
4. **Gönderim hatası** işlemi geri almaz, yalnızca loglanır ([ADR-019](019-email-via-smtp-provider-agnostic.md)). Log'a e-posta içeriği yazılmaz.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Yalnızca sonuçta e-posta | Taraf süresini bilmeden sessizliği sonuç doğurur. |
| Admin'e her yönetime taşınan talepte e-posta | Admin bildirim kanalı (B4) kararı yok; liste yeterli. |
| Süre dolmadan önce hatırlatma e-postası | İkinci bir zamanlama kuralı ister; ölçüm sonrası ayrı karar. |

## Consequences

### Olumlu
- Her süre başladığında ilgili taraf son tarihi e-postada görür.

### Olumsuz
- Talep başına en çok beş e-posta gider; çoğu işlemde bu sayı iki ya da üçtür.

### Nötr / Trade-off
- Hatırlatma yokluğu, sessizliğin sonuç doğurduğu kararın (ADR-083) riskidir; e-posta bunu açıkça yazar.

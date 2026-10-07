# [ADR-019] E-posta SMTP üzerinden, sağlayıcıdan bağımsız ve akışı kesmeden gönderilir

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-account-and-session` design.md D2

---

## Context

Kimlik dilimi iki tür e-posta gönderir: doğrulama bağlantısı ve ikinci faktör kodu. Hangi e-posta sağlayıcısının kullanılacağı ve üretim barındırması henüz karara bağlanmadı (`openspec/KALANLAR.md` B4, B5). E-posta teslimi kimliğin ön koşulu olmamalıdır ([ADR-017](017-email-verification-non-blocking-and-2fa-switch.md) §4).

## Decision

- Gönderim `spring-boot-starter-mail` ile **SMTP** üzerinden yapılır ([ADR-002](002-dependency-budget.md) bütçesine kullanıcı onayıyla eklendi). Sunucu bilgileri yalnızca ortamdan okunur; geliştirmede hedef `mailpit`'tir.
- `MailSender` bir arayüzdür. Testler gerçek SMTP'ye çıkmaz, kaydeden bir uygulamayla değiştirilir (`AGENTS.md` K10).
- Gönderim hatası **fırlatılmaz**: `WARN` olarak, alıcı adresi maskelenerek loglanır ve kullanıcı akışı devam eder.
- İçerik düz metin ve tek bağlantıdır; şablon motoru kullanılmaz.
- Gönderim istek içinde yapılır; kuyruk yoktur.

| Alternatif | Neden reddedildi |
|---|---|
| Sağlayıcının HTTP API'si | Sağlayıcı kilidi; bu dilimi açık B4 kararına bağımlı kılar. |
| Gönderimi kuyruğa almak (asenkron) | Tek sunuculu MVP için kuyruk §3 over-engineering yasağına girer; hata zaten akışı kesmiyor. |
| Şablon motoru (Thymeleaf vb.) | Bütçe dışı paket; iki düz metin e-posta için gereksiz. |
| Gönderim hatasında kaydı geri almak | E-posta teslimini kimliğin ön koşulu yapar; ADR-017 bunu reddetti. |

## Consequences

### Olumlu
- Sağlayıcı kararı kod değişikliği gerektirmez: her sağlayıcı SMTP konuşur, yalnızca ortam değişkenleri değişir.
- Geliştirmede gönderilen her e-posta `mailpit` arayüzünde görünür; gerçek gönderim olmaz.

### Olumsuz
- Gönderim isteği bekletir (zaman aşımı 5 saniye). Yavaş bir SMTP sunucusu kayıt yanıtını yavaşlatır.
- Teslim edilemeyen e-posta yalnızca sunucu log'unda görünür; üretim izlemesi B5 kararına bağlı.

### Nötr / Trade-off
- Düz metin e-posta sade görünür; markalı HTML e-posta ihtiyacı doğarsa şablon kararı ayrıca alınır.

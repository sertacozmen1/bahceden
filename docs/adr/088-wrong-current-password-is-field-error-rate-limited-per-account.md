# [ADR-088] Yanlış mevcut parola alan hatasıdır; parola değiştirme hesap başına hız sınırlıdır

**Tarih:** 2026-10-17
**Durum:** Accepted
**Kaynak:** `add-password-change` design.md D2

---

## Context

[ADR-087](087-password-change-endpoint-requires-current-password.md) mevcut parolayı zorunlu kılar. Yanlış parolanın nasıl bildirileceği ve tahmin denemelerinin nasıl durdurulacağı ayrıca karar ister.

Oturumlu uçlarda **401** "oturum yok veya geçersiz" (`AUTH_REQUIRED`) anlamındadır ve `web` oturum durumunu 401 ile okur. Formlar `fieldErrors`'ı ilgili alanın altına bağlar (`AGENTS.md` §12.3).

Çalınmış bir oturumu kullanan saldırgan istekleri farklı adreslerden gönderebilir. Hız sınırı bellekte, sabit pencerelidir ([ADR-022](022-in-memory-fixed-window-rate-limiting.md)).

## Decision

- Mevcut parola yanlışsa **400** `VALIDATION_FAILED` döner ve hata `fieldErrors` içinde `currentPassword` alanına bağlanır.
- Uç hesap başına **15 dakikada 5 istek** ile sınırlanır (`RateLimitPolicy.PASSWORD_CHANGE`).
  - Sınır, parola denetiminden **önce** uygulanır.
  - Başarılı istekler de sayılır.
  - Aşılırsa **429** `RATE_LIMITED` döner, mevcut parola doğru olsa bile.
- Yanlış denemeler hesabı kilitlemez.

| Alternatif | Neden reddedildi |
|---|---|
| **401** `AUTH_INVALID_CREDENTIALS` | Geçerli oturumun form hatasını oturum hatasıyla karıştırır; form hatayı alana bağlayamaz. |
| Yeni hata kodu (örn. 422 `CURRENT_PASSWORD_INVALID`) | Alan hatası olan bir durum için ikinci bir eşleme yolu açar. |
| İstemci adresine göre sınır | Tek oturumla dağıtık deneme yapılabilir. |
| Yanlış denemelerde hesabı kilitlemek | Kilit, hesabı başkasının kapatabileceği bir düğmeye dönüşür; hız sınırı yeterli. |

## Consequences

### Olumlu
- Kullanıcı hangi alanın yanlış olduğunu görür; oturumu açık kalır.
- Çalınmış bir oturumla mevcut parolayı tahmin etmek pratikte imkânsızdır (saatte en çok 20 deneme).

### Olumsuz
- Mevcut parolasını birkaç kez yanlış yazan kullanıcı 15 dakika bekler.
- Sayaç uygulama yeniden başladığında sıfırlanır (ADR-022'nin bilinen sınırı).

### Nötr / Trade-off
- Aynı desen yeni doğrulama bağlantısı isteğinde de var (`VERIFICATION_REQUEST`, hesap anahtarlı).

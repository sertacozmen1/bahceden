# [ADR-097] Süresi geçmiş kimlik kayıtları saklama payından sonra silinir; yenileme token'ları aileyle

**Tarih:** 2026-10-20
**Durum:** Accepted
**Kaynak:** `add-stale-record-cleanup` design.md D2

---

## Context

E-posta doğrulama, parola sıfırlama, giriş kodu ve yenileme token'ı satırları süreleri dolduktan sonra da tabloda kalıyordu (`00-current-state.md` L15). Süresi dolmuş satır hiçbir akışta kabul edilmez; her biri bir hesaba bağlıdır.

Yenileme token'ları ailelerle zincirlenir. Kullanılmış bir token tekrar sunulursa, süresi geçmiş olsa bile, ailenin tamamı iptal edilir ([ADR-018](018-refresh-token-rotation-with-family-revocation.md), `SessionService.refresh`). Bu, çalınmış eski bir token'ı yakalayan korumadır.

`common` paketi alan paketlerine bağımlı olamaz (`AGENTS.md` §3).

## Decision

- İş (`ExpiredTokenCleanup`, `account` paketi) [ADR-096](096-unpaid-orders-closed-by-scheduled-sweep.md) ile aynı aralıkta ama ondan bağımsız çalışır. `bahceden.cleanup.token-retention` (7 gün) kadar önce süresi dolmuş satırları siler:
  - `email_verification_tokens`, `password_reset_tokens`, `login_challenges`: `expires_at < kesim`;
  - `refresh_tokens`: ailesinin en geç süresi kesimden önce olan **ailelerin bütün satırları**.
- Silme toplu sorguyla, her tablo için ayrı transaction'da yapılır. Bir tablonun hatası diğerlerini durdurmaz; o tablo sonraki turda yeniden denenir.
- Log'a yalnızca silinen satır sayıları yazılır.
- Hesaplar silinmez.

| Alternatif | Neden reddedildi |
|---|---|
| Yenileme token'larını tek tek süresine göre silmek | Canlı ailenin eski token'ı silinirse tekrar sunulduğunda "bilinmeyen token" olur ve aile iptal edilmez. |
| Süre dolar dolmaz silmek | Destek ve olay incelemesi için iz kalmaz. |
| `pg_cron` veya bölümlü tablolar | Yeni eklenti ve işletme yükü (K7, §3). |
| Hiç silmemek | Veri en aza indirme ilkesine (KVKK) ve tablo büyümesine aykırı. |

## Consequences

### Olumlu
- Hesaba bağlı, işe yaramayan kayıtlar birikmez.
- Tekrar kullanım koruması zayıflamaz; testle sabit.

### Olumsuz
- Uzun yaşayan bir oturum zincirinin eski satırları, zincirin son token'ının süresi dolup 7 gün geçene kadar kalır.
- 7 günden eski bir doğrulama veya sıfırlama sorununda iz bulunmaz.

### Nötr / Trade-off
- Saklama payı tek ayardır; hukuk veya destek ihtiyacına göre değiştirilebilir.

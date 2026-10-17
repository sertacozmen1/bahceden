# [ADR-087] Oturum açıkken parola değiştirme: `POST /accounts/me/password-changes`, mevcut parola zorunlu

**Tarih:** 2026-10-17
**Durum:** Accepted
**Kaynak:** `add-password-change` design.md D1

---

## Context

Oturum açmış kullanıcı parolasını yalnızca e-postadaki sıfırlama bağlantısıyla değiştirebiliyordu ([ADR-035](035-password-reset-signs-out-everywhere-no-auto-login.md)). Parolasının sızdığından şüphelenen kullanıcı için bu, çıkış yapıp gelen kutusuna gitmek demekti.

Projede durum değiştiren eylemler komut adıyla yazılıyor: `me/verification-requests`, `password-resets/confirmations`. Parola okunabilir bir kaynak değil.

Kilitlenmemiş bir cihazda açık kalan oturum, parolayı bilmeyen birinin elinde olabilir.

## Decision

- Uç `POST /api/v1/accounts/me/password-changes`. Oturum ister; hesap token'dan gelir.
- Gövde `{ "currentPassword", "newPassword" }`. Yeni parola kayıt kurallarına uyar (10–200 karakter).
- Mevcut parola hash ile doğrulanır.
- Yeni parola mevcut parolayla aynıysa `newPassword` alanında doğrulama hatası döner.
- Başarı **204**'tür; gövde yoktur.

| Alternatif | Neden reddedildi |
|---|---|
| `PATCH` / `PUT /accounts/me/password` | Okunamayan bir "parola" kaynağını güncelliyormuş gibi gösterir; projede eylemler komut adıyla yazılıyor. |
| Mevcut parolayı istememek | Açık kalan oturumu ele geçiren hesabı da kalıcı olarak ele geçirir. |
| Aynı parolaya izin vermek | Kullanıcıya hiçbir şey kazandırmadan bütün cihazlarından çıkarır ([ADR-089](089-password-change-signs-out-everywhere.md)). |

## Consequences

### Olumlu
- Parola, gelen kutusuna gitmeden birkaç saniyede değişir.
- Açık kalan bir oturum tek başına parolayı değiştirmeye yetmez.

### Olumsuz
- Mevcut parolasını unutan kullanıcı bu yolu kullanamaz; sıfırlama akışına gider.

### Nötr / Trade-off
- Sözleşmeye bir uç ve bir şema eklenir; kırıcı değişiklik yoktur.

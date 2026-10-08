# [ADR-046] Admin yüzeyi `web` içinde `(admin)` rota grubunda; ilk admin veritabanından atanır

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-seller-onboarding` design.md D7

---

## Context

[ADR-037](037-seller-application-with-document-and-manual-approval.md) başvuruların admin tarafından karara bağlanmasını istiyor ve admin ekranını bu dilimin kapsamına koyuyor. `ROLE_ADMIN` bugün yalnızca veritabanından verilebiliyor ([ADR-008](008-jwt-auth-with-opaque-refresh.md)).

[ADR-001](001-spring-api-and-next-web.md) iki dağıtılabilir birim seçti. MVP'de admin sayısı bir ya da iki kişi.

## Decision

- **Sayfalar `web` içinde:** `/admin/sellers` (durum sekmeleri; varsayılan sekme bekleyenler) ve `/admin/sellers/[id]` (detay, belge, geçmiş, karar). Dosyalar `web/src/app/(admin)/` altında.
- **Kapı `requireRole("ROLE_ADMIN")`:** oturum yoksa giriş sayfası, rol yoksa "bulunamadı"; admin yüzeyinin varlığı açığa çıkmaz. Başlıktaki "Yönetim" bağlantısı yalnızca bu rolü taşıyan hesapta görünür. Bu kapılar yalnızca deneyimdir.
- **Otorite `api`'dir.** `/api/v1/admin/sellers/**` uçları `SecurityConfig`'te `hasRole("ADMIN")` ile korunur. Rolsüz istek `403 AUTH_FORBIDDEN`'ı ortak hata gövdesiyle alır; gövdeyi `ApiAccessDeniedHandler` yazar.
- **Durum geçişleri komut uçlarıdır:** `POST …/{id}/approve | reject | suspend | reinstate`. Ret ve askı 10–500 karakterlik bir gerekçe ister.
- **`ROLE_ADMIN` ürün yüzeyinden verilemez.** İlk admin veritabanında atanır ([Admin yetkisi verme](../how-to/admin-yetkisi-verme.md)). Dev seed'i `admin@ornek.test` hesabını içerir.

| Alternatif | Neden reddedildi |
|---|---|
| Ayrı bir admin uygulaması veya hazır bir admin paneli | Yeni dağıtılabilir birim ya da yeni paket demek (K7); §3'e aykırı. |
| Kararları doğrudan veritabanında vermek | Rol, e-posta ve denetim izi kuralları atlanır; karar izlenemez. |
| Ürün yüzeyinden admin atamak | Bugün ihtiyaç yok; en yüksek değerli saldırı yüzeyini açar. |

## Consequences

### Olumlu
- Mevcut oturum, BFF, tasarım sistemi ve i18n altyapısı olduğu gibi kullanıldı.
- Rol verme yüzeyi hiç yok.

### Olumsuz
- Admin ataması bir veritabanı işlemi; bunu yapacak kişinin üretim veritabanı erişimi olmalı.
- Admin rolü kaldırılsa bile erişim token'ı en fazla 15 dakika daha geçerli kalır (ADR-008).

### Nötr / Trade-off
- `PageResponse`'un sabit şema adı kaldırıldı: generic kayıt artık öğe tipine göre ayrı şemalar üretiyor (`PageResponseProductSummary`, `PageResponseAdminSellerSummary`). JSON gövdesi değişmedi; değişen tek şey `web`'deki üretilen tip adı.

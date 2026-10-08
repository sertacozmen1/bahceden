# [ADR-042] Başvuru tek bir multipart istektir; yeniden gönderim de öyle

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-seller-onboarding` design.md D3

---

## Context

Başvuru, beyan alanlarını (üretici adı, şehir, telefon, belge türü) ve bir belge dosyasını birlikte taşır ([ADR-037](037-seller-application-with-document-and-manual-approval.md)). Satıcı için en kısa yol tek ekran ve tek gönderimdir (`AGENTS.md` §12.5). Belgesiz bir başvurunun admin için anlamı yoktur.

## Decision

- **`POST /api/v1/sellers/me` kaydı oluşturur, `PUT /api/v1/sellers/me` reddedilen kaydı yeniden gönderir.** İkisi de `multipart/form-data` alır.
- **Gövde düz form alanlarından oluşur:** `displayName`, `city`, `phone`, `documentType` ve dosya olarak `document`. Alanlar `@ModelAttribute` ile tek bir istek kaydına bağlanır. Bean Validation bütün alanlarda, dosyanın varlığı dahil, çalışır.
- **`documentType` metin olarak alınır ve izinli değerlerin kalıbıyla doğrulanır.** Tanınmayan değer, okunamayan istek (500) değil, yerelleştirilmiş alan hatası (400) olur.
- **Kayıt ve dosya tek bir işlem olarak ele alınır.** Doğrulama hatasında hiçbir şey yazılmaz. Dosya diske yazılıp veritabanı transaction'ı geri alınırsa, dosya `afterCompletion` adımında silinir.
- Yeniden gönderimde yeni bir belge satırı eklenir; önceki belge silinmez.

| Alternatif | Neden reddedildi |
|---|---|
| Önce JSON ile kayıt, sonra ayrı bir uçla belge yükleme | Belgesiz yetim kayıt ve sahipsiz dosya oluşur; istemci iki hatayı ayrı yönetir. |
| Belgeyi Base64 ile JSON içinde taşımak | Boyut yaklaşık üçte bir büyür, dosya bellekte tamamen tutulur, sözleşmedeki ikili alan anlamsızlaşır. |
| Beyanı JSON parçası, belgeyi dosya parçası olarak göndermek | İlk taslak buydu. JSON parçasındaki tanınmayan enum değeri okunamayan istek olarak 500'e düşüyor, yerelleştirilmiş alan hatası üretilemiyordu. |

## Consequences

### Olumlu
- Tek gönderim, tek hata gövdesi: istemci alan hatalarını (`document` dahil) tek yerde eşler.
- springdoc düz alanlardan anlamlı bir multipart şeması üretiyor (`SellerApplicationRequest`).

### Olumsuz
- `web` BFF, multipart gövdeyi okuyup `api`'ye yeniden kurarak iletir; JSON uçlarından ayrı bir yardımcı gerekti (`handleBffFormMutation`).
- Dosyayı silme adımı da başarısız olursa yetim dosya kalır; bu durum loglanır.

### Nötr / Trade-off
- Bean Validation'ın göremediği kurallar (dosya türü ve boyutu, telefon biçimi) servis katmanında saptanır ve aynı `fieldErrors` biçimiyle döner (`ApiException.invalidField`).

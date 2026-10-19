# account-identity Specification (delta)

> Bu bir **delta spec**'tir: bu change'in getirdiği davranışı tanımlar. Uygulanıp onaylandıktan sonra `openspec/specs/account-identity/spec.md` ana spec'ine sync edilir (`AGENTS.md` §5.3). Kararlar: `design.md` D2.

## ADDED Requirements

### Requirement: Süresi geçmiş kimlik kayıtları temizlenir

Sistem, süresi yapılandırılmış saklama payı (7 gün) kadar önce dolmuş e-posta doğrulama token'larını, parola sıfırlama token'larını ve giriş kodlarını zamanlanmış bir işle SHALL siler. Yenileme token'ları yalnızca ailesinin bütün token'larının süresi saklama payı kadar önce dolmuşsa, aileyle birlikte silinir. Hesaplar silinmez. Log'a yalnızca silinen satır sayıları yazılır.

#### Scenario: Süresi geçmiş tek kullanımlık token'lar silinir
- **WHEN** bir doğrulama token'ının, bir sıfırlama token'ının ve bir giriş kodunun süresi saklama payından daha önce dolmuştur
- **THEN** iş üçünü de siler

#### Scenario: Saklama payı içindeki token'lar kalır
- **WHEN** bir token'ın süresi dolmuştur ama saklama payı henüz geçmemiştir, ya da süresi dolmamıştır
- **THEN** iş o satıra dokunmaz

#### Scenario: Yenileme token'ı ailesi canlıyken silinmez
- **WHEN** bir ailenin eski, kullanılmış token'ının süresi saklama payından daha önce dolmuştur ama ailenin daha yeni bir token'ı hâlâ geçerlidir
- **THEN** iş ailenin hiçbir satırını silmez ve eski token tekrar sunulursa aile yine iptal edilir

#### Scenario: Süresi tümüyle geçmiş aile silinir
- **WHEN** bir ailenin bütün yenileme token'larının süresi saklama payından daha önce dolmuştur
- **THEN** iş ailenin bütün satırlarını siler

# [ADR-084] Talep uçlarında yetki: alıcı, satıcı ve admin uçları ayrı; fotoğraflar yalnızca üç tarafa korumalı sunulur

**Tarih:** 2026-10-13
**Durum:** Accepted
**Kaynak:** `add-defect-claims` design.md D6

---

## Context

Talep üç tarafın eylemidir: alıcı açar ve yanıtlar, satıcı yanıtlar, admin karar verir. Sahiplik sorgu seviyesinde denetlenir ([ADR-007](007-query-level-ownership-scoping.md)); satıcı önce onaylı satıcı kapısından geçer ([ADR-044](044-seller-role-follows-status-with-query-gate.md)).

Talep fotoğrafı alıcının evinden çekilmiş olabilir ve kişisel veridir (K10). Satıcı belgeleri için korumalı sunum deseni zaten var ([ADR-045](045-seller-documents-table-content-sniffing-protected-serving.md)).

## Decision

1. **Uçlar ve denetim:**

   | Taraf | Uçlar | Denetim |
   |---|---|---|
   | Alıcı | `POST …/orders/{orderNumber}/seller-orders/{id}/claim`, `…/claim/accept`, `…/claim/escalate` | `orders/**` altında oturum; sipariş sorgusu alıcı kimliğiyle kurulur |
   | Satıcı | `POST /api/v1/sellers/me/orders/{id}/claim/response`, `…/claim/preview` | onaylı satıcı kapısı, sonra `seller_id`'li kilitli sorgu |
   | Admin | `/api/v1/admin/claims/**` | `ROLE_ADMIN` |
   | Fotoğraf | `GET /api/v1/claims/{claimId}/photos/{position}?size=card\|large` | oturum; talep alıcı hesabı, satıcı hesabı veya admin rolüyle bulunur |

2. **Hata yanıtları:**
   - Başkasının talebi veya fotoğrafı her uçta 404 döner.
   - Durumu uymayan eylem 409 `CLAIM_INVALID_TRANSITION` alır.
   - Alanlar alan hatası olarak döner.
3. **Fotoğraf sunumu:** `ProtectedFileResponses` ile, `nosniff` ve `Cache-Control: private, no-store` başlıklarıyla. `web` fotoğrafı kendi Route Handler'ıyla aktarır; token tarayıcıya gitmez ([ADR-023](023-web-bff-cookies-and-header-csrf.md)).
4. **Log:** açıklama, gerekçe ve fotoğraf anahtarı uygulama log'una yazılmaz; yalnızca talebin iç kimliği yazılır.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Fotoğrafları ilan fotoğrafı gibi herkese açık sunmak | Kişisel veri; anahtar tahmin edilemez olsa da bağlantı paylaşılabilir. |
| Tek bir ortak talep ucu ve rol dallanması | Rol bazlı dallanma sahiplik hatasına açık; ayrı uçlar ayrı sorgu kurar. |
| Fotoğrafı imzalı geçici URL ile sunmak | URL paylaşılırsa süresi boyunca açıktır; mevcut desen oturum ister. |

## Consequences

### Olumlu
- Her uç kendi sahiplik sorgusunu kurar; yetki hatası varlığı sızdırmaz.

### Olumsuz
- Fotoğraf için `web`'de ek bir aktarma rotası gerekir.

### Nötr / Trade-off
- Admin her talebin fotoğrafını görür; bu, hakemliğin gereğidir.

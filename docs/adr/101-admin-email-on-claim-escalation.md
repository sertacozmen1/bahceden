# [ADR-101] Talep yönetime düşünce her admin hesabına kendi dilinde e-posta gider

**Tarih:** 2026-10-21
**Durum:** Accepted
**Kaynak:** `change-deadline-sweeps-and-admin-claim-email` design.md D2

---

## Context

Talep iki yoldan `ESCALATED` olur: alıcı teklif ya da reddi yönetime taşır veya satıcının süresi dolar. Bu durumda karar admin'e kalır ([ADR-080](080-defect-claim-tables-and-state-machine.md), [ADR-084](084-defect-claim-authorization-and-protected-photos.md)). [ADR-085](085-defect-claim-emails.md) alıcıya ve satıcıya e-posta gönderiyordu. Admin'e ise e-postayı "bildirim kanalı kararı (B4) yok" gerekçesiyle reddetmişti. Admin talebi yalnızca listeyi açınca görüyordu (`00-current-state.md` L40).

O günden bu yana bütün taraf bildirimleri B4'ü beklemeden e-postayla eklendi ([ADR-078](078-shipping-notifications-email-only-three-events.md), [ADR-091](091-delivered-email-to-buyer-with-claim-deadline.md)). B4'ün açık sorusu SMS ve sağlayıcısıdır. Admin hesapları veritabanından verilir ve az sayıdadır ([ADR-046](046-admin-surface-in-web-first-admin-by-sql.md)).

## Decision

- Talep `ESCALATED` olduğunda, alıcı ve satıcı e-postalarına ek olarak `ROLE_ADMIN` rolündeki **her** hesaba ayrı bir e-posta gider. Hesaplar `AccountRepository.findByRole` ile bulunur.
- E-posta, hesabın dilindedir ([ADR-032](032-account-locale-stored-and-refreshed-on-login.md)) ve şunları içerir:
  - sipariş numarası;
  - neden: "alıcı bildirimi yönetime taşıdı" ya da "üretici süresinde yanıt vermedi". Neden, yönetime düşüren geçişten gelir (`BUYER_ESCALATE` / `DEADLINE_ESCALATE`);
  - admin talep sayfası bağlantısı (`/admin/claims/{id}`).
- Alıcının açıklaması, adı, adresi ve fotoğrafı e-postaya girmez; admin bunları korumalı sayfada görür.
- Gönderim, alıcı ve satıcı e-postalarıyla aynı listede, transaction'dan sonra yapılır. Hata geçişi geri almaz ([ADR-019](019-email-via-smtp-provider-agnostic.md)).
- Admin hesabı yoksa admin e-postası gitmez; bu hata değildir.
- Bu karar ADR-085'in 3. maddesinin ("admin'e e-posta gitmez") yerini alır. B4 (SMS) açık kalır.

| Alternatif | Neden reddedildi |
|---|---|
| Yapılandırmada tek ekip adresi | Admin rolüyle ayrışır: rolü alınan kişi e-posta almaya devam eder, yeni admin almaz. |
| Günlük özet e-postası | Zamanlanmış ek iş; bekleyen talep hacmi ölçülmeden gerekmez (§3). |
| E-postaya talep açıklamasını koymak | Alıcının serbest metni kişisel veri içerebilir (K10). |
| Yalnızca süre dolunca bildirmek | Alıcının taşıdığı talep de aynı kararı bekler. |

## Consequences

### Olumlu
- Kararı verecek kişi, talep yönetime düştüğü anda haberdar olur.
- Admin yetkisi tek kaynaktır: rol veren ve alan aynı zamanda bildirimi de verir veya alır.

### Olumsuz
- Her yönetime düşen talep, admin sayısı kadar e-posta üretir.
- Admin rolü alınan biri, rolün alındığı ana kadar gönderilmiş e-postalardaki bağlantıyı açamaz (403). Bağlantı yetki vermez.

### Nötr / Trade-off
- Admin'e başka olaylarda (teslim edilemeyen sipariş, satıcı başvurusu, iadesi sürekli başarısız talep) e-posta gitmez. Her biri ayrı bir ihtiyaçtır.

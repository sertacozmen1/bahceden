# [ADR-095] Geliştirme alıcısı yeni bir dev changeset'iyle doğrulanmış yapılır

**Tarih:** 2026-10-19
**Durum:** Accepted
**Kaynak:** `add-checkout-email-verification` design.md D3

---

## Context

Belgelenmiş geliştirme akışları (sipariş, kargo, ayıplı ürün) `alici@ornek.test` hesabıyla başlıyor ([Yerel geliştirme](../how-to/yerel-gelistirme.md)). Bu hesabın e-postası `004` dev seed'inde doğrulanmamış açılmıştı. [ADR-093](093-order-requires-verified-email.md)'ten sonra bu hesap sipariş veremez.

Uygulanmış changeset düzenlenemez (`AGENTS.md` K8); Liquibase checksum'ı açılışı düşürür. Dev verisi `contextFilter:dev` ile işaretlenir ve test bağlamında uygulanmadığı testle sabittir ([ADR-014](014-dev-seed-via-profiled-repeatable-migration.md), [ADR-016](016-liquibase-for-schema-migrations.md)).

## Decision

- Yeni changeset `015-dev-seed-verified-buyer.sql` (`contextFilter:dev`): `alici@ornek.test` hesabını, doğrulanmamışsa, doğrulanmış yapar. İdempotenttir. `--rollback` hesabı yeniden doğrulanmamış yapar.
- `DevSeedIsolationTest` bu changeset'in test bağlamında uygulanmadığını da sabitler.
- Kapıyı geliştirmede denemek için yeni hesap açılır; doğrulama bağlantısı mailpit'tedir.

| Alternatif | Neden reddedildi |
|---|---|
| Hesabı doğrulanmamış bırakmak | Her geliştirme akışı bir mailpit adımı daha ister. |
| Doğrulanmamış ikinci bir dev hesabı eklemek | Kayıt formu bunu zaten sağlıyor; seed büyür. |
| `004`'ü düzenlemek | Checksum hatası; K8 ihlali. |

## Consequences

### Olumlu
- Belgelenmiş akışlar değişmeden çalışır.
- Kapı, gerçek kullanıcının yolundan (kayıt → doğrulama) denenir.

### Olumsuz
- Dev veritabanında doğrulanmamış hazır bir hesap yok; kapıyı görmek için kayıt gerekir.

### Nötr / Trade-off
- Dev seed sayısı bir artar.

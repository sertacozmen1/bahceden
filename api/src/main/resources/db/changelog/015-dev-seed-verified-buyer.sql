--liquibase formatted sql

-- Geliştirme alıcısının e-postası doğrulanmış olur (YALNIZCA dev bağlamı).
--
-- 002, 004, 008, 010 ve 012 ile aynı kurallar: yalnızca dev bağlamında uygulanır
-- (ADR-014, ADR-016), veri tamamen SAHTEDİR (AGENTS.md K10) ve idempotenttir.
--
-- DİKKAT: Bu dosyadaki yorumlarda "changeset" kelimesinin ardından "ad:değer"
-- biçimi kullanılmaz; ayrıştırıcı bunu gerçek bir bildirim sanar.
--
-- Sipariş artık doğrulanmış e-posta ister (add-checkout-email-verification D1).
-- Belgelenmiş geliştirme akışları (sipariş, kargo, ayıplı ürün) alici@ornek.test
-- ile başladığı için hesap doğrulanmış yapılır (D3). 004 uygulanmış olduğu için
-- düzenlenmez (K8). Kapıyı denemek için yeni hesap açılır.

--changeset bahceden:015-dev-seed-verified-buyer contextFilter:dev
--comment: Gelistirme icin alici@ornek.test hesabinin e-postasi dogrulanmis yapilir
update accounts set email_verified = true, updated_at = now()
where email = 'alici@ornek.test' and email_verified = false;
--rollback update accounts set email_verified = false, updated_at = now() where email = 'alici@ornek.test';

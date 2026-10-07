--liquibase formatted sql

-- Geliştirme hesapları (YALNIZCA dev bağlamı).
--
-- 002 ile aynı kurallar: yalnızca dev bağlamında uygulanır (ADR-014,
-- ADR-016), veri tamamen SAHTEDİR (AGENTS.md K10) ve idempotenttir.
--
-- DİKKAT: Bu dosyadaki yorumlarda "changeset" kelimesinin ardından "ad:değer"
-- biçimi kullanılmaz; ayrıştırıcı bunu gerçek bir bildirim sanar.
--
-- İki hesap (design.md Migration Plan adım 2):
--   alici@ornek.test     ikinci faktör KAPALI: olağan tek adımlı giriş
--   iki-adim@ornek.test  ikinci faktör AÇIK: ürün yüzeyinden açılamayan
--                        anahtarın açık yolunu elle denemek için (D8)
--
-- İkisinin de parolası: gelistirme-parolasi-1
-- Bu parola yalnızca yerel geliştirme veritabanında geçerlidir ve bilinçli
-- olarak depodadır; dev bağlamı üretimde etkinleştirilmez. Hash'ler BCrypt,
-- maliyet 12 (PasswordHasher ile aynı).

--changeset bahceden:004-dev-seed-account contextFilter:dev
--comment: Gelistirme icin iki sahte hesap; biri ikinci faktoru acik
insert into accounts (email, password_hash, status, email_verified, two_factor_enabled)
values ('alici@ornek.test', '$2a$12$Xjg2JZcnX7HEEm/0agRfyODOkvO0zrsPeg1nh24qfHM.DMRE6xxH2', 'ACTIVE', false, false),
       ('iki-adim@ornek.test', '$2a$12$42adRUB//VUbO.TOi7a5dejdTJG6B2J4xIWcWHjrIQM8AepZjuoUC', 'ACTIVE', true, true)
on conflict (email) do nothing;

insert into account_roles (account_id, role)
select id, 'ROLE_CUSTOMER' from accounts where email in ('alici@ornek.test', 'iki-adim@ornek.test')
on conflict (account_id, role) do nothing;
--rollback delete from accounts where email in ('alici@ornek.test', 'iki-adim@ornek.test');

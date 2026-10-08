--liquibase formatted sql

-- Satıcı başvurusu için geliştirme hesapları (YALNIZCA dev bağlamı).
--
-- 002 ve 004 ile aynı kurallar: yalnızca dev bağlamında uygulanır (ADR-014,
-- ADR-016), veri tamamen SAHTEDİR (AGENTS.md K10) ve idempotenttir.
--
-- DİKKAT: Bu dosyadaki yorumlarda "changeset" kelimesinin ardından "ad:değer"
-- biçimi kullanılmaz; ayrıştırıcı bunu gerçek bir bildirim sanar.
--
-- İki hesap (add-seller-onboarding design.md Migration Plan adım 2):
--   admin@ornek.test    ROLE_ADMIN: başvuruları karara bağlamak için (D7)
--   uretici@ornek.test  e-postası doğrulanmış, satıcı kaydı YOK: başvuru elle
--                       yapılır, çünkü belge dosyası seed ile volume'a yazılamaz
--
-- İkisinin de parolası 004 ile aynıdır (gelistirme-parolasi-1); hash aynı
-- parolanın BCrypt özetidir. Yalnızca yerel geliştirme veritabanında geçerlidir.

--changeset bahceden:008-dev-seed-seller-onboarding contextFilter:dev
--comment: Gelistirme icin admin hesabi ve satici kaydi olmayan dogrulanmis uretici hesabi
insert into accounts (email, password_hash, status, email_verified, two_factor_enabled)
values ('admin@ornek.test', '$2a$12$Xjg2JZcnX7HEEm/0agRfyODOkvO0zrsPeg1nh24qfHM.DMRE6xxH2', 'ACTIVE', true, false),
       ('uretici@ornek.test', '$2a$12$Xjg2JZcnX7HEEm/0agRfyODOkvO0zrsPeg1nh24qfHM.DMRE6xxH2', 'ACTIVE', true, false)
on conflict (email) do nothing;

insert into account_roles (account_id, role)
select id, 'ROLE_CUSTOMER' from accounts where email in ('admin@ornek.test', 'uretici@ornek.test')
on conflict (account_id, role) do nothing;

insert into account_roles (account_id, role)
select id, 'ROLE_ADMIN' from accounts where email = 'admin@ornek.test'
on conflict (account_id, role) do nothing;
--rollback delete from account_roles where account_id in (select id from accounts where email in ('admin@ornek.test', 'uretici@ornek.test'));
--rollback delete from accounts where email in ('admin@ornek.test', 'uretici@ornek.test');

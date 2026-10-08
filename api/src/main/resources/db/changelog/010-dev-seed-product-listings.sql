--liquibase formatted sql

-- Ürün ilanı akışı için geliştirme satıcısı (YALNIZCA dev bağlamı).
--
-- 002, 004 ve 008 ile aynı kurallar: yalnızca dev bağlamında uygulanır
-- (ADR-014, ADR-016), veri tamamen SAHTEDİR (AGENTS.md K10) ve idempotenttir.
--
-- DİKKAT: Bu dosyadaki yorumlarda "changeset" kelimesinin ardından "ad:değer"
-- biçimi kullanılmaz; ayrıştırıcı bunu gerçek bir bildirim sanar.
--
-- uretici@ornek.test (008) için hesaplı ve ONAYLI bir satıcı kaydı açar; hesap
-- zaten bir satıcı kaydına sahipse (örneğin elle başvurulup onaylandıysa)
-- hiçbir şey yapmaz. Fotoğraflar Liquibase ile volume'a yazılamadığı için
-- ilan içermez; ilanlar elle eklenir (add-product-listings design.md).

--changeset bahceden:010-dev-seed-product-listings contextFilter:dev
--comment: Gelistirme icin uretici@ornek.test hesabina bagli onayli satici kaydi ve satici rolu
insert into sellers (account_id, slug, display_name, city, status, phone, status_changed_at)
select id, 'uretici-bahcesi', 'Üretici Bahçesi', 'Manisa', 'APPROVED', '+905550000000', now()
from accounts
where email = 'uretici@ornek.test'
  and not exists (select 1 from sellers s where s.account_id = accounts.id)
on conflict do nothing;

insert into account_roles (account_id, role)
select a.id, 'ROLE_SELLER'
from accounts a
join sellers s on s.account_id = a.id and s.status = 'APPROVED'
where a.email = 'uretici@ornek.test'
on conflict (account_id, role) do nothing;
--rollback delete from account_roles where role = 'ROLE_SELLER' and account_id in (select id from accounts where email = 'uretici@ornek.test');
--rollback delete from sellers where slug = 'uretici-bahcesi';

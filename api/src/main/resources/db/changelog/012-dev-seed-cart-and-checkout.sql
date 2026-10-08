--liquibase formatted sql

-- Sepet ve sipariş akışı için geliştirme verisi (YALNIZCA dev bağlamı).
--
-- 002, 004, 008 ve 010 ile aynı kurallar: yalnızca dev bağlamında uygulanır
-- (ADR-014, ADR-016), veri tamamen SAHTEDİR (AGENTS.md K10) ve idempotenttir.
--
-- DİKKAT: Bu dosyadaki yorumlarda "changeset" kelimesinin ardından "ad:değer"
-- biçimi kullanılmaz; ayrıştırıcı bunu gerçek bir bildirim sanar.
--
-- uretici@ornek.test satıcısına sahte ama sağlaması geçerli bir ödeme hesabı
-- ve sahte sağlayıcının alt üye anahtarı verilir: ürünleri satın alınabilir
-- olur (add-cart-and-checkout D1, D2). alici@ornek.test'e İzmir'de sahte bir
-- teslimat adresi eklenir. Kayıt zaten varsa hiçbir şey yapılmaz.

--changeset bahceden:012-dev-seed-cart-and-checkout contextFilter:dev
--comment: Gelistirme icin uretici@ornek.test saticisina sahte odeme hesabi, alici@ornek.test hesabina sahte adres
insert into seller_payout_accounts (seller_id, legal_type, legal_name, tax_id, iban, status, provider_sub_merchant_key)
select s.id, 'INDIVIDUAL', 'Örnek Üretici', '10000000078', 'TR330006100519786457841326', 'ACTIVE', 'fake-sub-gelistirme'
from sellers s
join accounts a on a.id = s.account_id
where a.email = 'uretici@ornek.test'
on conflict (seller_id) do nothing;

insert into addresses (account_id, title, recipient_name, phone, province_code, district, address_line, postal_code)
select a.id, 'Ev', 'Örnek Alıcı', '+905550000000', '35', 'Bornova', 'Ornek Mahallesi, Ornek Sokak No: 1 Daire: 2', '35040'
from accounts a
where a.email = 'alici@ornek.test'
  and not exists (select 1 from addresses x where x.account_id = a.id);
--rollback delete from addresses where account_id in (select id from accounts where email = 'alici@ornek.test') and title = 'Ev';
--rollback delete from seller_payout_accounts where provider_sub_merchant_key = 'fake-sub-gelistirme';

-- Geliştirme verisi (yalnızca dev profili).
--
-- Bu dosya yalnızca `dev` profilinde Flyway konumlarına eklenir
-- (application-dev.yaml); üretimde çalışmaz (design.md D5).
--
-- Veri tamamen SAHTEDİR: gerçek üretici adı, adres veya iletişim bilgisi
-- içermez (AGENTS.md K10). Idempotenttir: tekrar çalıştığında kopya üretmez,
-- bu yüzden tekrarlanabilir (R__) migration olarak yazıldı.
--
-- Fiyatlar kuruş cinsindendir (ADR-006): 2450 = 24,50 TL.

insert into sellers (slug, display_name, city)
values ('ornek-uretici-manisa', 'Örnek Üretici (Manisa)', 'Manisa'),
       ('ornek-uretici-antalya', 'Örnek Üretici (Antalya)', 'Antalya')
on conflict (slug) do nothing;

insert into products (seller_id, slug, name, description, status, unit,
                      price_amount, price_currency, stock_quantity, published_at)
select s.id, v.slug, v.name, v.description, v.status, v.unit,
       v.price_amount, 'TRY', v.stock_quantity, v.published_at
from (values
    ('ornek-uretici-manisa',  'sultani-uzum',      'Sultani Üzüm',      'Çekirdeksiz, bağdan yeni toplanmış.',        'PUBLISHED', 'KILOGRAM', 4250, 120, now() - interval '1 day'),
    ('ornek-uretici-manisa',  'domates-salkim',    'Salkım Domates',    'Sera değil, tarla domatesi.',                'PUBLISHED', 'KILOGRAM', 2450, 80,  now() - interval '3 day'),
    ('ornek-uretici-manisa',  'kuru-incir',        'Kuru İncir',        'Doğal kurutma, kükürtsüz.',                  'PUBLISHED', 'KILOGRAM', 18900, 25, now() - interval '5 day'),
    ('ornek-uretici-antalya', 'avokado-hass',      'Avokado (Hass)',    'Ağaçta olgunlaşmış, tek tek sarılı.',        'PUBLISHED', 'PIECE',    3500, 200, now() - interval '2 day'),
    ('ornek-uretici-antalya', 'maydanoz',          'Maydanoz',          'Sabah toplanmış, demet halinde.',            'PUBLISHED', 'BUNCH',    1500, 300, now() - interval '4 day'),
    -- Vitrinde GÖRÜNMEMESİ gereken kayıtlar: dört durumun ve süzgecin elle
    -- doğrulanabilmesi için bilinçli olarak seed'de duruyor.
    ('ornek-uretici-antalya', 'taslak-portakal',   'Taslak Portakal',   'Henüz yayınlanmadı.',                        'DRAFT',     'KILOGRAM', 2000, 50,  null),
    ('ornek-uretici-manisa',  'arsivlenmis-kiraz', 'Arşivlenmiş Kiraz', 'Sezonu kapandı.',                            'ARCHIVED',  'KILOGRAM', 9500, 0,   now() - interval '60 day')
) as v(seller_slug, slug, name, description, status, unit, price_amount, stock_quantity, published_at)
join sellers s on s.slug = v.seller_slug
on conflict (slug) do nothing;

-- Katalog: üretici ve ürün tabloları.
--
-- Şema yalnızca Flyway ile değişir; bu dosya uygulandıktan sonra DÜZENLENMEZ,
-- düzeltme yeni bir migration'dır (ADR-004).
--
-- sellers tablosu bilinçli olarak minimumdur (design.md D4): hesap ilişkisi,
-- vergi/belge alanları ve onay durumu satıcı onboarding kararıyla (KALANLAR A3)
-- V2 migration'ı olarak gelecektir.

create table sellers (
    id           bigserial    primary key,
    slug         varchar(120) not null,
    display_name varchar(160) not null,
    city         varchar(80)  not null,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    constraint sellers_slug_unique unique (slug)
);

comment on table sellers is 'Ürünü üreten ve gönderen üretici. Hesap ilişkisi A3 kararıyla eklenecek.';

create table products (
    id             bigserial    primary key,
    seller_id      bigint       not null,
    slug           varchar(140) not null,
    name           varchar(200) not null,
    description    text,
    -- Yayın durumu enum'dur, boolean değil (design.md D1): üçüncü bir durum
    -- (ör. ön onay) geldiğinde şema göçü ve ikinci bayrak gerekmez.
    status         varchar(20)  not null,
    unit           varchar(20)  not null,
    -- Para minor unit (kuruş) tamsayı + açık para birimi (ADR-006).
    price_amount   bigint       not null,
    -- varchar(3), char(3) değil: PostgreSQL char'ı bpchar olarak raporluyor ve
    -- Hibernate şema doğrulaması (ddl-auto: validate) bunu uyumsuz sayıyor.
    price_currency varchar(3)   not null,
    stock_quantity integer      not null,
    published_at   timestamptz,
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now(),
    constraint products_slug_unique unique (slug),
    constraint products_seller_fk foreign key (seller_id) references sellers (id),
    constraint products_status_check check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    constraint products_unit_check check (unit in ('KILOGRAM', 'PIECE', 'BUNCH')),
    constraint products_price_amount_check check (price_amount >= 0),
    constraint products_stock_check check (stock_quantity >= 0),
    -- Yayınlanmış ürünün yayın tarihi olmak zorundadır: vitrin sıralaması buna
    -- dayanıyor, geçersiz durum veritabanında temsil edilemez.
    constraint products_published_requires_date check (status <> 'PUBLISHED' or published_at is not null)
);

comment on table products is 'Satılan ürün ilanı. Vitrin yalnızca PUBLISHED olanları gösterir.';

-- Vitrin sorgusunun tam deseni: status süzgeci + (published_at desc, id desc)
-- sıralaması. Kısmi indeks, yayınlanmamış ilanları indekste taşımaz.
create index products_storefront_idx
    on products (published_at desc, id desc)
    where status = 'PUBLISHED';

create index products_seller_idx on products (seller_id);

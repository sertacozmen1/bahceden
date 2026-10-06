--liquibase formatted sql

-- Katalog: üretici ve ürün tabloları.
--
-- Uygulanmış changeset DÜZENLENMEZ: düzeltme yeni bir changeset'tir (ADR-016).
-- Liquibase checksum doğrular; bu dosya değişirse uygulama açılışta durur.
--
-- sellers tablosu bilinçli olarak minimumdur (ADR-013): hesap ilişkisi,
-- vergi/belge alanları ve onay durumu satıcı onboarding kararıyla
-- (KALANLAR A3) ayrı bir changeset olarak gelecektir.

--changeset bahceden:001-create-catalog
--comment: Katalog şeması: sellers ve products tabloları, kısıtlar ve indeksler
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
    status         varchar(20)  not null,
    unit           varchar(20)  not null,
    price_amount   bigint       not null,
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
    constraint products_published_requires_date check (status <> 'PUBLISHED' or published_at is not null)
);

comment on table products is 'Satılan ürün ilanı. Vitrin yalnızca PUBLISHED olanları gösterir.';

create index products_storefront_idx
    on products (published_at desc, id desc)
    where status = 'PUBLISHED';

create index products_seller_idx on products (seller_id);
--rollback drop index if exists products_seller_idx;
--rollback drop index if exists products_storefront_idx;
--rollback drop table if exists products;
--rollback drop table if exists sellers;

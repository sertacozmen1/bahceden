--liquibase formatted sql

-- Ürün ilanları (add-product-listings; ADR-038, ADR-039, design.md D1, D3).
--
-- products.status'a REMOVED eklenir: admin tarafından gerekçeyle kaldırılmış
-- ilan. Satıcı bu durumdan kendisi çıkamaz (ADR-038 §3). Kısıt düşürülüp yeni
-- değerle yeniden eklenir; uygulanmış 001 changeset'ine dokunulmaz (ADR-016).
--
-- Fotoğraflar ayrı tabloda, açık bir pozisyonla tutulur; kapak position = 0
-- olan satırdır (D3). Dosyaların kendisi veritabanında değil, depolama
-- dizinindedir (ADR-039); burada yalnızca sunucunun ürettiği anahtarlar durur.
--
-- product_status_changes YALNIZCA EKLENEN bir geçmiştir (ADR-040 deseni).
--
-- Ekleyici değişiklik: yeni kolonlar ve tablolar; durum kısıtı genişler.

--changeset bahceden:009-product-listings
--comment: Urun ilanlari: REMOVED durumu, gerekce kolonlari, fotograf ve durum gecmisi tablolari
alter table products drop constraint products_status_check;
alter table products add constraint products_status_check
    check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'REMOVED'));

alter table products
    add column status_reason     varchar(500),
    add column status_changed_at timestamptz;

comment on column products.status_reason is 'Admin kaldirma gerekcesi; saticiya gosterilir (ADR-038 par. 3).';

create table product_photos (
    id         bigserial   primary key,
    product_id bigint      not null,
    position   integer     not null,
    card_key   varchar(64) not null,
    large_key  varchar(64) not null,
    width      integer     not null,
    height     integer     not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint product_photos_product_fk foreign key (product_id) references products (id),
    constraint product_photos_position_unique unique (product_id, position) deferrable initially deferred,
    constraint product_photos_card_key_unique unique (card_key),
    constraint product_photos_large_key_unique unique (large_key),
    constraint product_photos_position_check check (position between 0 and 4),
    constraint product_photos_size_check check (width > 0 and height > 0)
);

comment on table product_photos is 'Ilan fotograflari; position 0 kapak. Orijinal saklanmaz, iki boyut JPEG (design.md D4).';

create table product_status_changes (
    id               bigserial   primary key,
    product_id       bigint      not null,
    from_status      varchar(20),
    to_status        varchar(20) not null,
    reason           varchar(500),
    actor_account_id bigint      not null,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint product_status_changes_product_fk foreign key (product_id) references products (id),
    constraint product_status_changes_actor_fk foreign key (actor_account_id) references accounts (id),
    constraint product_status_changes_from_check check (from_status is null or from_status in ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'REMOVED')),
    constraint product_status_changes_to_check check (to_status in ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'REMOVED'))
);

comment on table product_status_changes is 'Ilan durum gecislerinin silinmeyen gecmisi (design.md D1).';

create index product_status_changes_product_idx on product_status_changes (product_id, created_at, id);

-- Satıcı panelindeki "ilanlarım" listesi: en son güncellenen üstte.
create index products_seller_updated_idx on products (seller_id, updated_at desc, id desc);
--rollback drop index if exists products_seller_updated_idx;
--rollback drop table if exists product_status_changes;
--rollback drop table if exists product_photos;
--rollback alter table products drop column if exists status_changed_at;
--rollback alter table products drop column if exists status_reason;
--rollback alter table products drop constraint products_status_check;
--rollback alter table products add constraint products_status_check check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED'));

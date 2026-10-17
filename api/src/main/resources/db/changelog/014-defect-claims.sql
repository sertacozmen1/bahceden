--liquibase formatted sql

-- Ayıplı ürün talebi (add-defect-claims; ADR-079…084, design.md D1–D5).
--
-- Bütün şema bu tek changeset'tedir: change'in sonraki fazları şemayı
-- değiştirmez (tasks.md 1.1).
--
-- defect_claims: satıcı siparişi başına tek talep (ADR-079). Satıcı siparişinin
-- durumu değişmez; sonucu (iade tutarı, yeni komisyon ve hakediş) talep taşır.
-- defect_claim_lines: talep satırları; istenen, önerilen ve iade edilen adet (ADR-080).
-- defect_claim_photos: 1–3 fotoğraf, iki boyut (ADR-081); yalnızca taraflara sunulur (ADR-084).
-- defect_claim_changes: YALNIZCA EKLENEN geçmiş; eylemci boşsa süre işidir (ADR-083).
--
-- Talepler ve fotoğraflar silinmez (yasal kayıt; saklama süresi KALANLAR A5).
--
-- Ekleyici değişiklik: yeni tablolar ve genişleyen bir iade nedeni kısıtı.
--
-- Geri alma notu: DEFECT nedenli iade varken eski refunds_reason_check geri
-- kurulamaz; geri alma önce bu satırların elle ele alınmasını ister.

--changeset bahceden:014-defect-claims
--comment: Ayipli urun talebi, satirlari, fotograflari ve gecmisi; DEFECT iade nedeni
create table defect_claims (
    id                 bigserial     primary key,
    seller_order_id    bigint        not null,
    status             varchar(20)   not null,
    description        varchar(1000) not null,
    seller_reason      varchar(500),
    seller_deadline_at timestamptz   not null,
    buyer_deadline_at  timestamptz,
    refund_amount      bigint,
    shipping_refunded  boolean       not null default false,
    commission_after   bigint,
    payout_after       bigint,
    resolved_by        varchar(10),
    resolved_at        timestamptz,
    created_at         timestamptz   not null default now(),
    updated_at         timestamptz   not null default now(),
    constraint defect_claims_seller_order_fk foreign key (seller_order_id) references seller_orders (id),
    constraint defect_claims_seller_order_unique unique (seller_order_id),
    constraint defect_claims_status_check check (status in ('OPEN', 'OFFERED', 'REJECTED', 'ESCALATED', 'REFUNDED',
        'CLOSED')),
    constraint defect_claims_resolved_by_check check (resolved_by is null or resolved_by in ('SELLER', 'BUYER', 'ADMIN',
        'DEADLINE')),
    constraint defect_claims_amounts_check check ((refund_amount is null or refund_amount > 0)
        and (commission_after is null or commission_after >= 0) and (payout_after is null or payout_after >= 0))
);

comment on table defect_claims is 'Ayipli urun talebi; satici siparisi basina tek (ADR-079, ADR-080).';
comment on column defect_claims.description is 'Alicinin aciklamasi; kisisel veri olabilir, log''a yazilmaz (ADR-084).';

create index defect_claims_status_created_idx on defect_claims (status, created_at desc, id desc);
create index defect_claims_open_deadline_idx on defect_claims (seller_deadline_at) where status = 'OPEN';
create index defect_claims_buyer_deadline_idx on defect_claims (buyer_deadline_at) where status in ('OFFERED', 'REJECTED');

create table defect_claim_lines (
    id                bigserial   primary key,
    claim_id          bigint      not null,
    order_item_id     bigint      not null,
    claimed_quantity  integer     not null,
    offered_quantity  integer,
    refunded_quantity integer,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    constraint defect_claim_lines_claim_fk foreign key (claim_id) references defect_claims (id),
    constraint defect_claim_lines_order_item_fk foreign key (order_item_id) references order_items (id),
    constraint defect_claim_lines_item_unique unique (claim_id, order_item_id),
    constraint defect_claim_lines_quantity_check check (claimed_quantity between 1 and 99
        and (offered_quantity is null or offered_quantity between 0 and claimed_quantity)
        and (refunded_quantity is null or refunded_quantity between 0 and 99))
);

create index defect_claim_lines_claim_idx on defect_claim_lines (claim_id, id);

create table defect_claim_photos (
    id         bigserial   primary key,
    claim_id   bigint      not null,
    position   integer     not null,
    card_key   varchar(64) not null,
    large_key  varchar(64) not null,
    width      integer     not null,
    height     integer     not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint defect_claim_photos_claim_fk foreign key (claim_id) references defect_claims (id),
    constraint defect_claim_photos_position_unique unique (claim_id, position),
    constraint defect_claim_photos_card_key_unique unique (card_key),
    constraint defect_claim_photos_large_key_unique unique (large_key),
    constraint defect_claim_photos_position_check check (position between 0 and 2),
    constraint defect_claim_photos_size_check check (width > 0 and height > 0)
);

create table defect_claim_changes (
    id               bigserial   primary key,
    claim_id         bigint      not null,
    from_status      varchar(20),
    to_status        varchar(20) not null,
    reason           varchar(500),
    actor_account_id bigint,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint defect_claim_changes_claim_fk foreign key (claim_id) references defect_claims (id),
    constraint defect_claim_changes_actor_fk foreign key (actor_account_id) references accounts (id)
);

comment on table defect_claim_changes is 'Talep gecislerinin silinmeyen gecmisi; eylemci bossa sure isi (ADR-083).';

create index defect_claim_changes_claim_idx on defect_claim_changes (claim_id, created_at, id);

alter table refunds drop constraint refunds_reason_check;
alter table refunds add constraint refunds_reason_check
    check (reason in ('OUT_OF_STOCK', 'PAYMENT_EXPIRED', 'CANCELLED', 'UNDELIVERED', 'DEFECT'));
--rollback alter table refunds drop constraint refunds_reason_check;
--rollback alter table refunds add constraint refunds_reason_check check (reason in ('OUT_OF_STOCK', 'PAYMENT_EXPIRED', 'CANCELLED', 'UNDELIVERED'));
--rollback drop table if exists defect_claim_changes;
--rollback drop table if exists defect_claim_photos;
--rollback drop table if exists defect_claim_lines;
--rollback drop table if exists defect_claims;

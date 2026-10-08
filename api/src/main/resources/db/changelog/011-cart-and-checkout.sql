--liquibase formatted sql

-- Sepet, sipariş ve ödeme (add-cart-and-checkout; ADR-054…058, design.md D1–D9).
--
-- Bütün tablolar tek changeset'tedir: change'in sonraki fazları şemayı
-- değiştirmez (tasks.md 1.1).
--
-- Tutarlar kuruş cinsinden bigint'tir (ADR-006); siparişin para birimi tek
-- kolonda durur ve satıcı siparişleri, satırlar onu paylaşır.
--
-- Sipariş anındaki fiyat, kargo ücreti, komisyon ve teslimat adresi kopyalanır
-- (D6): sonradan değişen ürün, adres veya yapılandırma geçmiş siparişi
-- etkilemez. Sipariş ve ödeme kayıtları silinmez (AGENTS.md §11.4).
--
-- order_status_changes YALNIZCA EKLENEN bir geçmiştir (ADR-040 deseni).
--
-- Ekleyici değişiklik: products'a varsayılanlı bir kolon ve yeni tablolar.

--changeset bahceden:011-cart-and-checkout
--comment: Sepet, adres, siparis, odeme hesabi, odeme ve iade tablolari; ilanda kargoya verme suresi
alter table products
    add column dispatch_days integer not null default 2,
    add constraint products_dispatch_days_check check (dispatch_days between 1 and 3);

comment on column products.dispatch_days is 'Kargoya verme suresi, is gunu (ADR-058).';

create table seller_payout_accounts (
    id                        bigserial    primary key,
    seller_id                 bigint       not null,
    legal_type                varchar(20)  not null,
    legal_name                varchar(200) not null,
    tax_id                    varchar(11)  not null,
    tax_office                varchar(80),
    iban                      varchar(26)  not null,
    status                    varchar(20)  not null,
    provider_sub_merchant_key varchar(100),
    created_at                timestamptz  not null default now(),
    updated_at                timestamptz  not null default now(),
    constraint seller_payout_accounts_seller_fk foreign key (seller_id) references sellers (id),
    constraint seller_payout_accounts_seller_unique unique (seller_id),
    constraint seller_payout_accounts_legal_type_check check (legal_type in ('INDIVIDUAL', 'SOLE_PROPRIETOR', 'COMPANY')),
    constraint seller_payout_accounts_status_check check (status in ('ACTIVE', 'PROVIDER_REJECTED')),
    constraint seller_payout_accounts_key_check check (status <> 'ACTIVE' or provider_sub_merchant_key is not null)
);

comment on table seller_payout_accounts is 'Saticinin odeme hesabi; IBAN ve kimlik numarasi yanitta maskelenir, loglanmaz (design.md D2).';

create table carts (
    id         bigserial   primary key,
    account_id bigint      not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint carts_account_fk foreign key (account_id) references accounts (id),
    constraint carts_account_unique unique (account_id)
);

create table cart_items (
    id         bigserial   primary key,
    cart_id    bigint      not null,
    product_id bigint      not null,
    quantity   integer     not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint cart_items_cart_fk foreign key (cart_id) references carts (id),
    constraint cart_items_product_fk foreign key (product_id) references products (id),
    constraint cart_items_cart_product_unique unique (cart_id, product_id),
    constraint cart_items_quantity_check check (quantity between 1 and 99)
);

comment on table cart_items is 'Sepet satiri; fiyat tutulmaz, her okumada urunden gelir (design.md D4).';

create table addresses (
    id             bigserial    primary key,
    account_id     bigint       not null,
    title          varchar(40)  not null,
    recipient_name varchar(120) not null,
    phone          varchar(16)  not null,
    province_code  varchar(2)   not null,
    district       varchar(80)  not null,
    address_line   varchar(250) not null,
    postal_code    varchar(5),
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now(),
    constraint addresses_account_fk foreign key (account_id) references accounts (id)
);

create index addresses_account_idx on addresses (account_id, id);

create table orders (
    id                   bigserial    primary key,
    order_number         varchar(16)  not null,
    buyer_account_id     bigint       not null,
    status               varchar(30)  not null,
    idempotency_key      varchar(64)  not null,
    ship_recipient_name  varchar(120) not null,
    ship_phone           varchar(16)  not null,
    ship_province_code   varchar(2)   not null,
    ship_district        varchar(80)  not null,
    ship_address_line    varchar(250) not null,
    ship_postal_code     varchar(5),
    items_total          bigint       not null,
    shipping_total       bigint       not null,
    grand_total          bigint       not null,
    currency             varchar(3)   not null,
    payment_expires_at   timestamptz  not null,
    paid_at              timestamptz,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now(),
    constraint orders_buyer_fk foreign key (buyer_account_id) references accounts (id),
    constraint orders_number_unique unique (order_number),
    constraint orders_idempotency_unique unique (buyer_account_id, idempotency_key),
    constraint orders_status_check check (status in ('PENDING_PAYMENT', 'PAID', 'PAYMENT_FAILED', 'PAYMENT_EXPIRED', 'CANCELLED_OUT_OF_STOCK')),
    constraint orders_amounts_check check (items_total >= 0 and shipping_total >= 0 and grand_total = items_total + shipping_total),
    constraint orders_paid_at_check check (status <> 'PAID' or paid_at is not null)
);

comment on table orders is 'Alicinin siparisi; adres ve tutarlar siparis aninda kopyalanir, silinmez (design.md D6).';

create index orders_buyer_created_idx on orders (buyer_account_id, created_at desc, id desc);

create table seller_orders (
    id                 bigserial    primary key,
    order_id           bigint       not null,
    seller_id          bigint       not null,
    status             varchar(20)  not null,
    items_total        bigint       not null,
    shipping_fee       bigint       not null,
    commission_rate_bp integer      not null,
    commission_fixed   bigint       not null,
    commission_amount  bigint       not null,
    seller_payout      bigint       not null,
    dispatch_days      integer      not null,
    dispatch_due_at    timestamptz,
    cancelled_by       varchar(10),
    cancel_reason      varchar(500),
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    constraint seller_orders_order_fk foreign key (order_id) references orders (id),
    constraint seller_orders_seller_fk foreign key (seller_id) references sellers (id),
    constraint seller_orders_order_seller_unique unique (order_id, seller_id),
    constraint seller_orders_status_check check (status in ('PENDING_PAYMENT', 'NEW', 'CANCELLED', 'VOID')),
    constraint seller_orders_cancelled_by_check check (cancelled_by is null or cancelled_by in ('BUYER', 'SELLER')),
    constraint seller_orders_amounts_check check (items_total >= 0 and shipping_fee >= 0 and commission_amount >= 0
        and commission_amount <= items_total and seller_payout = items_total - commission_amount),
    constraint seller_orders_dispatch_days_check check (dispatch_days between 1 and 3)
);

comment on table seller_orders is 'Siparisin bir saticiya dusen kismi: kargo, komisyon, hakedis ve iptal birimi (ADR-055, design.md D6).';

create index seller_orders_seller_created_idx on seller_orders (seller_id, created_at desc, id desc);

create table order_items (
    id              bigserial    primary key,
    seller_order_id bigint       not null,
    product_id      bigint       not null,
    product_name    varchar(200) not null,
    product_slug    varchar(140) not null,
    unit            varchar(20)  not null,
    unit_price      bigint       not null,
    quantity        integer      not null,
    line_total      bigint       not null,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    constraint order_items_seller_order_fk foreign key (seller_order_id) references seller_orders (id),
    constraint order_items_product_fk foreign key (product_id) references products (id),
    constraint order_items_quantity_check check (quantity between 1 and 99),
    constraint order_items_amounts_check check (unit_price > 0 and line_total = unit_price * quantity)
);

create index order_items_seller_order_idx on order_items (seller_order_id, id);

create table order_status_changes (
    id               bigserial    primary key,
    seller_order_id  bigint       not null,
    from_status      varchar(20),
    to_status        varchar(20)  not null,
    reason           varchar(500),
    actor_account_id bigint       not null,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    constraint order_status_changes_seller_order_fk foreign key (seller_order_id) references seller_orders (id),
    constraint order_status_changes_actor_fk foreign key (actor_account_id) references accounts (id),
    constraint order_status_changes_from_check check (from_status is null or from_status in ('PENDING_PAYMENT', 'NEW', 'CANCELLED', 'VOID')),
    constraint order_status_changes_to_check check (to_status in ('PENDING_PAYMENT', 'NEW', 'CANCELLED', 'VOID'))
);

comment on table order_status_changes is 'Satici siparisi durum gecislerinin silinmeyen gecmisi (design.md D6).';

create index order_status_changes_seller_order_idx on order_status_changes (seller_order_id, created_at, id);

create table payments (
    id                 bigserial    primary key,
    order_id           bigint       not null,
    provider           varchar(20)  not null,
    provider_reference varchar(100) not null,
    redirect_url       varchar(500) not null,
    status             varchar(20)  not null,
    amount             bigint       not null,
    currency           varchar(3)   not null,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    constraint payments_order_fk foreign key (order_id) references orders (id),
    constraint payments_order_unique unique (order_id),
    constraint payments_reference_unique unique (provider, provider_reference),
    constraint payments_status_check check (status in ('INITIATED', 'SUCCEEDED', 'FAILED')),
    constraint payments_amount_check check (amount >= 0)
);

comment on table payments is 'Bolunmus odeme kaydi; sonuc saglayicidan dogrulanir (ADR-054, design.md D8).';

create table refunds (
    id                 bigserial    primary key,
    payment_id         bigint       not null,
    seller_order_id    bigint,
    reason             varchar(20)  not null,
    amount             bigint       not null,
    provider_reference varchar(100) not null,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    constraint refunds_payment_fk foreign key (payment_id) references payments (id),
    constraint refunds_seller_order_fk foreign key (seller_order_id) references seller_orders (id),
    constraint refunds_reason_check check (reason in ('OUT_OF_STOCK', 'PAYMENT_EXPIRED', 'CANCELLED')),
    constraint refunds_amount_check check (amount > 0)
);

comment on table refunds is 'Iadeler; seller_order_id bos ise odemenin tamami (stok yetersizligi veya suresi dolan odeme, design.md D8, D9).';

create index refunds_payment_idx on refunds (payment_id);
--rollback drop table if exists refunds;
--rollback drop table if exists payments;
--rollback drop table if exists order_status_changes;
--rollback drop table if exists order_items;
--rollback drop table if exists seller_orders;
--rollback drop table if exists orders;
--rollback drop table if exists addresses;
--rollback drop table if exists cart_items;
--rollback drop table if exists carts;
--rollback drop table if exists seller_payout_accounts;
--rollback alter table products drop constraint if exists products_dispatch_days_check;
--rollback alter table products drop column if exists dispatch_days;

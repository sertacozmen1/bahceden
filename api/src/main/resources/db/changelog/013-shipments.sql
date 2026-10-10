--liquibase formatted sql

-- Kargo gönderisi (add-shipments; ADR-057, design.md D2, D7).
--
-- Bütün şema değişikliği tek changeset'tedir: change'in sonraki fazları şemayı
-- değiştirmez (tasks.md 1.1).
--
-- Gönderi ayrı tablodadır (D2): iptal edilip yeniden açılan gönderinin izi
-- kalır, silinmez (AGENTS.md §11.4). Bir satıcı siparişinin aynı anda tek
-- ETKİN gönderisi olur; kısmi benzersiz indeks bunu veritabanında zorunlu kılar.
--
-- Satıcı siparişine kargo durumları eklenir; geçmiş tablosu sistem (zamanlanmış
-- durum sorgusu) geçişlerini yapan hesap olmadan yazabilsin diye
-- actor_account_id boş geçilebilir olur. İadeye "teslim edilemedi" nedeni eklenir.
--
-- Ekleyici değişiklik: mevcut satırlar yeni kısıtları zaten sağlar.

--changeset bahceden:013-shipments
--comment: Gonderiler tablosu; satici siparisi kargo durumlari; sistem gecisleri; teslim edilemeyen iadesi
create table shipments (
    id                 bigserial    primary key,
    seller_order_id    bigint       not null,
    integrator         varchar(20)  not null,
    integrator_ref     varchar(100) not null,
    carrier            varchar(80)  not null,
    tracking_number    varchar(60)  not null,
    package_size       varchar(10)  not null,
    status             varchar(20)  not null,
    label_key          varchar(32)  not null,
    label_content_type varchar(40)  not null,
    handed_over_at     timestamptz,
    delivered_at       timestamptz,
    last_polled_at     timestamptz,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    constraint shipments_seller_order_fk foreign key (seller_order_id) references seller_orders (id),
    constraint shipments_package_size_check check (package_size in ('SMALL', 'MEDIUM', 'LARGE')),
    constraint shipments_status_check check (status in ('CREATED', 'HANDED_OVER', 'IN_TRANSIT', 'DELIVERED',
                                                        'DELIVERY_FAILED', 'CANCELLED')),
    constraint shipments_label_type_check check (label_content_type in ('application/pdf', 'image/png')),
    constraint shipments_delivered_at_check check (status <> 'DELIVERED' or delivered_at is not null)
);

comment on table shipments is 'Kargo gonderileri; iptal edilen silinmez, satici siparisi basina tek etkin gonderi (ADR-057, design.md D2).';

create unique index shipments_active_seller_order_uq on shipments (seller_order_id) where status <> 'CANCELLED';
create index shipments_open_poll_idx on shipments (status, last_polled_at);

alter table seller_orders drop constraint seller_orders_status_check;
alter table seller_orders add constraint seller_orders_status_check
    check (status in ('PENDING_PAYMENT', 'NEW', 'CANCELLED', 'VOID', 'SHIPPED', 'DELIVERED', 'DELIVERY_FAILED',
                      'REFUNDED', 'CLOSED'));

alter table order_status_changes drop constraint order_status_changes_from_check;
alter table order_status_changes add constraint order_status_changes_from_check
    check (from_status is null or from_status in ('PENDING_PAYMENT', 'NEW', 'CANCELLED', 'VOID', 'SHIPPED',
                                                  'DELIVERED', 'DELIVERY_FAILED', 'REFUNDED', 'CLOSED'));
alter table order_status_changes drop constraint order_status_changes_to_check;
alter table order_status_changes add constraint order_status_changes_to_check
    check (to_status in ('PENDING_PAYMENT', 'NEW', 'CANCELLED', 'VOID', 'SHIPPED', 'DELIVERED', 'DELIVERY_FAILED',
                         'REFUNDED', 'CLOSED'));
alter table order_status_changes alter column actor_account_id drop not null;

comment on column order_status_changes.actor_account_id is 'Gecisi yapan hesap; bos ise sistem (gonderi durumu sorgusu, design.md D2).';

alter table refunds drop constraint refunds_reason_check;
alter table refunds add constraint refunds_reason_check
    check (reason in ('OUT_OF_STOCK', 'PAYMENT_EXPIRED', 'CANCELLED', 'UNDELIVERED'));
--rollback alter table refunds drop constraint refunds_reason_check;
--rollback alter table refunds add constraint refunds_reason_check check (reason in ('OUT_OF_STOCK', 'PAYMENT_EXPIRED', 'CANCELLED'));
--rollback alter table order_status_changes alter column actor_account_id set not null;
--rollback alter table order_status_changes drop constraint order_status_changes_to_check;
--rollback alter table order_status_changes add constraint order_status_changes_to_check check (to_status in ('PENDING_PAYMENT', 'NEW', 'CANCELLED', 'VOID'));
--rollback alter table order_status_changes drop constraint order_status_changes_from_check;
--rollback alter table order_status_changes add constraint order_status_changes_from_check check (from_status is null or from_status in ('PENDING_PAYMENT', 'NEW', 'CANCELLED', 'VOID'));
--rollback alter table seller_orders drop constraint seller_orders_status_check;
--rollback alter table seller_orders add constraint seller_orders_status_check check (status in ('PENDING_PAYMENT', 'NEW', 'CANCELLED', 'VOID'));
--rollback drop table if exists shipments;

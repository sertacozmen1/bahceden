--liquibase formatted sql

-- Transactional outbox (ADR-110, change add-redis-cache-and-order-paid-event D4).
--
-- İş olayı, onu doğuran veriyle AYNI transaction'da buraya yazılır; zamanlanmış
-- relay yayınlanmamış satırları sırayla Kafka'ya gönderir ve işaretler. Kafka
-- kapalıyken olay kaybolmaz, burada bekler. Gövde (payload) kişisel veri taşımaz:
-- yalnızca kimlikler ve tutar (K10, ADR-111).
--
-- Kısmi indeks yalnızca yayınlanmamış satırları kapsar: relay her turda onları
-- kimlik sırasıyla okur, tablo büyüse de indeks küçük kalır. İkinci indeks
-- yayınlanmış satırların saklama süresi sonunda silinmesi içindir.
-- Ekleyicidir; mevcut veri değişmez.

--changeset bahceden:017-outbox-events
--comment: Is olaylari icin transactional outbox tablosu
create table outbox_events (
    id             bigserial    primary key,
    event_id       uuid         not null,
    event_type     varchar(64)  not null,
    topic          varchar(128) not null,
    aggregate_key  varchar(64)  not null,
    payload        jsonb        not null,
    occurred_at    timestamptz  not null,
    published_at   timestamptz,
    attempts       integer      not null default 0,
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now(),
    constraint outbox_events_event_id_uk unique (event_id),
    constraint outbox_events_attempts_ck check (attempts >= 0)
);
create index outbox_events_unpublished_idx on outbox_events (id) where published_at is null;
create index outbox_events_published_at_idx on outbox_events (published_at) where published_at is not null;
--rollback drop table if exists outbox_events;

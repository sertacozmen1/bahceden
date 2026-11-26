--liquibase formatted sql

-- Tüketicilerin işlediği olaylar (ADR-112, change add-redis-cache-and-order-paid-event D6).
--
-- Outbox en az bir kez teslim eder (ADR-110): aynı olay bir tüketiciye iki kez
-- ulaşabilir. Tüketici olayı işlerken aynı transaction'da buraya (tüketici adı,
-- olay kimliği) yazar; satır zaten varsa olay işlenmiş sayılır ve atlanır.
-- Birincil anahtar bu tekrar korumasının kendisidir.
--
-- Satırlar outbox ile aynı saklama süresinden sonra silinir; ikinci indeks bu
-- temizlik içindir. Ekleyicidir; mevcut veri değişmez.

--changeset bahceden:018-processed-events
--comment: Tuketicilerin isledigi olaylar (tekrar teslim korumasi)
create table processed_events (
    consumer      varchar(64) not null,
    event_id      uuid        not null,
    processed_at  timestamptz not null,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint processed_events_pk primary key (consumer, event_id)
);
create index processed_events_processed_at_idx on processed_events (processed_at);
--rollback drop table if exists processed_events;

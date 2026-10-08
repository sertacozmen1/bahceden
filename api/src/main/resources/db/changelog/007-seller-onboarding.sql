--liquibase formatted sql

-- Satıcı başvurusu ve onayı (add-seller-onboarding; ADR-037, design.md D1, D2, D6).
--
-- Başvuru, sellers tablosundaki satırın kendisidir: PENDING açılır, admin
-- APPROVED / REJECTED yapar, onaylı satıcı SUSPENDED olabilir. Durum
-- geçişlerinin geçmişi ayrı ve YALNIZCA EKLENEN bir tabloda tutulur
-- (seller_status_changes); uygulama kodunda güncelleme veya silme yolu yoktur.
--
-- Mevcut satırlar (yalnızca dev seed'i) APPROVED olur: status kolonu bu
-- varsayılanla eklenir, sonra varsayılan KALDIRILIR. Yeni kayıtların durumunu
-- uygulama açıkça yazar; durumu unutan bir kod satıcıyı sessizce onaylı açamaz
-- (D2). Bu yüzden 002 dev seed'i bu changeset'ten SONRA yeniden çalışacak
-- biçimde değiştirilirse status vermek zorundadır.
--
-- account_id NULL olabilir: seed satıcılarının hesabı yok. Tekil kısıt bir
-- hesabın en fazla bir satıcı kaydı olmasını şema düzeyinde sağlar; PostgreSQL
-- tekil kısıtta birden fazla NULL'a izin verir.
--
-- Belge dosyasının kendisi veritabanında DEĞİL, yapılandırılmış dizindedir
-- (ADR-039); burada yalnızca sunucunun ürettiği ad ve meta veri tutulur.
--
-- Ekleyici değişiklik: yeni kolonlar ve tablolar, veri kaybı yok.

--changeset bahceden:007-seller-onboarding
--comment: Satici basvurusu: sellers durum ve hesap kolonlari, belge ve durum gecmisi tablolari
alter table sellers
    add column account_id        bigint,
    add column status            varchar(20) not null default 'APPROVED',
    add column phone             varchar(16),
    add column status_reason     varchar(500),
    add column status_changed_at timestamptz;

alter table sellers alter column status drop default;

alter table sellers
    add constraint sellers_account_unique unique (account_id),
    add constraint sellers_account_fk foreign key (account_id) references accounts (id),
    add constraint sellers_status_check check (status in ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'));

comment on column sellers.account_id is 'Satici kaydinin sahibi olan hesap; seed saticilarinda NULL (ADR-037).';
comment on column sellers.status is 'Basvuru ve satis durumu; vitrin yalnizca APPROVED saticinin urunlerini gosterir (ADR-038).';
comment on column sellers.phone is 'Iletisim telefonu, E.164 (+90...). Yalnizca admin gorur; kisisel veri (K10).';
comment on column sellers.status_reason is 'Son ret veya aski gerekcesi; saticiya gosterilir.';

create table seller_documents (
    id            bigserial    primary key,
    seller_id     bigint       not null,
    document_type varchar(30)  not null,
    storage_key   varchar(64)  not null,
    content_type  varchar(40)  not null,
    size_bytes    bigint       not null,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    constraint seller_documents_seller_fk foreign key (seller_id) references sellers (id),
    constraint seller_documents_storage_key_unique unique (storage_key),
    constraint seller_documents_type_check check (document_type in ('CKS_CERTIFICATE', 'TAX_CERTIFICATE')),
    constraint seller_documents_size_check check (size_bytes > 0)
);

comment on table seller_documents is 'Satici basvuru belgeleri. Her gonderim yeni satir; onceki belgeler denetim izi icin korunur (design.md D6).';

-- Kaydın güncel belgesi en son satırdır: sorgu satıcı + zaman üzerinden gider.
create index seller_documents_seller_idx on seller_documents (seller_id, created_at desc, id desc);

create table seller_status_changes (
    id               bigserial    primary key,
    seller_id        bigint       not null,
    from_status      varchar(20),
    to_status        varchar(20)  not null,
    reason           varchar(500),
    actor_account_id bigint       not null,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    constraint seller_status_changes_seller_fk foreign key (seller_id) references sellers (id),
    constraint seller_status_changes_actor_fk foreign key (actor_account_id) references accounts (id),
    constraint seller_status_changes_from_check check (from_status is null or from_status in ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    constraint seller_status_changes_to_check check (to_status in ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'))
);

comment on table seller_status_changes is 'Satici durum gecislerinin silinmeyen gecmisi: kim, ne zaman, neden (ADR-037 par. 7).';

create index seller_status_changes_seller_idx on seller_status_changes (seller_id, created_at, id);

-- Admin listesi durum süzgeciyle ve yeniden eskiye sayfalanır.
create index sellers_status_idx on sellers (status, created_at desc, id desc);
--rollback drop index if exists sellers_status_idx;
--rollback drop table if exists seller_status_changes;
--rollback drop table if exists seller_documents;
--rollback alter table sellers drop constraint if exists sellers_status_check;
--rollback alter table sellers drop constraint if exists sellers_account_fk;
--rollback alter table sellers drop constraint if exists sellers_account_unique;
--rollback alter table sellers drop column if exists status_changed_at;
--rollback alter table sellers drop column if exists status_reason;
--rollback alter table sellers drop column if exists phone;
--rollback alter table sellers drop column if exists status;
--rollback alter table sellers drop column if exists account_id;

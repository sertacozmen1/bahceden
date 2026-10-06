--liquibase formatted sql

-- Hesap ve oturum şeması.
--
-- Uygulanmış değişiklik DÜZENLENMEZ: düzeltme yeni bir kayıttır (ADR-016).
--
-- ADR-017: hesap doğrudan ACTIVE açılır. E-posta doğrulaması bir BİLGİdir
-- (email_verified), giriş koşulu değildir. E-postanın kimlik akışına girmesi
-- hesap bazlı two_factor_enabled anahtarına bağlıdır; anahtar varsayılan
-- kapalıdır ve ürün yüzeyinden açılamaz.
--
-- Token ve kodlar düz metin olarak SAKLANMAZ: yalnızca SHA-256 hash'leri
-- tutulur (design.md D1/D3/D8). Düz değer kullanıcıya bir kez verilir.

--changeset bahceden:003-create-account
--comment: Hesap, roller, e-posta dogrulama tokenlari, yenileme tokenlari ve ikinci faktor kodlari
create table accounts (
    id                 bigserial    primary key,
    email              varchar(320) not null,
    password_hash      varchar(100) not null,
    status             varchar(20)  not null,
    email_verified     boolean      not null default false,
    two_factor_enabled boolean      not null default false,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    constraint accounts_email_unique unique (email),
    -- Bugün tek değer; enum olarak tutuluyor ki ikinci durum (askıya alma)
    -- şema göçü değil tek satırlık bir kısıt değişikliği olsun (ADR-010).
    constraint accounts_status_check check (status in ('ACTIVE'))
);

comment on table accounts is 'Kullanici hesabi. Ayni kisi hem alici hem satici olabilir (ADR-008).';
comment on column accounts.email_verified is 'Dogrulama baglantisi kullanildi mi. Girisi ENGELLEMEZ (ADR-017).';
comment on column accounts.two_factor_enabled is 'E-posta ile ikinci faktor. Varsayilan kapali; urun yuzeyinden acilamaz (ADR-017).';

create table account_roles (
    account_id bigint      not null,
    role       varchar(30) not null,
    constraint account_roles_pk primary key (account_id, role),
    constraint account_roles_account_fk foreign key (account_id) references accounts (id) on delete cascade,
    constraint account_roles_check check (role in ('ROLE_CUSTOMER', 'ROLE_SELLER', 'ROLE_ADMIN'))
);

create table email_verification_tokens (
    id         bigserial   primary key,
    account_id bigint      not null,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now(),
    constraint email_verification_tokens_hash_unique unique (token_hash),
    constraint email_verification_tokens_account_fk foreign key (account_id) references accounts (id) on delete cascade
);

create index email_verification_tokens_expires_idx on email_verification_tokens (expires_at);

create table refresh_tokens (
    id         bigserial   primary key,
    account_id bigint      not null,
    family_id  uuid        not null,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    revoked_at timestamptz,
    created_at timestamptz not null default now(),
    constraint refresh_tokens_hash_unique unique (token_hash),
    constraint refresh_tokens_account_fk foreign key (account_id) references accounts (id) on delete cascade
);

-- Aile bazlı iptal (D1) bu indekse dayanır: bir token tekrar kullanıldığında
-- ailenin tamamı tek sorguda iptal edilir.
create index refresh_tokens_family_idx on refresh_tokens (family_id);
create index refresh_tokens_expires_idx on refresh_tokens (expires_at);

create table login_challenges (
    id            bigserial   primary key,
    account_id    bigint      not null,
    code_hash     varchar(64) not null,
    expires_at    timestamptz not null,
    used_at       timestamptz,
    attempt_count integer     not null default 0,
    created_at    timestamptz not null default now(),
    constraint login_challenges_hash_unique unique (code_hash),
    constraint login_challenges_account_fk foreign key (account_id) references accounts (id) on delete cascade,
    constraint login_challenges_attempts_check check (attempt_count >= 0)
);

create index login_challenges_expires_idx on login_challenges (expires_at);
--rollback drop table if exists login_challenges;
--rollback drop table if exists refresh_tokens;
--rollback drop table if exists email_verification_tokens;
--rollback drop table if exists account_roles;
--rollback drop table if exists accounts;

--liquibase formatted sql

-- Parola sıfırlama token'ları (add-password-reset design.md D1, ADR-033).
--
-- Şekil email_verification_tokens ile aynıdır ama tablo AYRIDIR: sıfırlama
-- token'ı hesabı devralma yetkisi verir, doğrulama token'ı yalnızca bir bilgiyi
-- işaretler. Ayrı tablo, birinin diğerinin yerine kabul edilmesini şema
-- düzeyinde imkânsız kılar.
--
-- Düz token SAKLANMAZ: yalnızca SHA-256 hash'i tutulur. Ömür 1 saattir; yeni
-- talep hesabın kullanılmamış eski token'larını used_at ile kapatır.
--
-- Ekleyici değişiklik: yeni tablo, mevcut veriye dokunmaz.

--changeset bahceden:006-create-password-reset-tokens
--comment: Parola sifirlama tokenlari
create table password_reset_tokens (
    id         bigserial   primary key,
    account_id bigint      not null,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now(),
    constraint password_reset_tokens_hash_unique unique (token_hash),
    constraint password_reset_tokens_account_fk foreign key (account_id) references accounts (id) on delete cascade
);

comment on table password_reset_tokens is 'Parola sifirlama baglantisi tokenlari; yalnizca hash saklanir (ADR-033).';

-- Yeni talep hesabın açık token'larını kapatır: bu sorgu hesap üzerinden gider.
create index password_reset_tokens_account_idx on password_reset_tokens (account_id);
create index password_reset_tokens_expires_idx on password_reset_tokens (expires_at);
--rollback drop table if exists password_reset_tokens;

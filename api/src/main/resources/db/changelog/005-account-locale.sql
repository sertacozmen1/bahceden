--liquibase formatted sql

-- Hesabın dili (add-i18n design.md D4, ADR-032).
--
-- Hesaba giden e-postalar (doğrulama bağlantısı, giriş kodu, "hesabınız zaten
-- var") bu dilde gönderilir. Kayıtta isteğin dili yazılır, her başarılı
-- girişte güncellenir. Değerler SupportedLocale enum'uyla aynıdır; yeni dil
-- eklemek bu kısıtı genişleten yeni bir changeset demektir.
--
-- Ekleyici değişiklik: varsayılan değer mevcut satırları doldurur, veri kaybı
-- yoktur.

--changeset bahceden:005-account-locale
--comment: Hesabin dili (tr, en)
alter table accounts add column locale varchar(8) not null default 'tr';
alter table accounts add constraint accounts_locale_check check (locale in ('tr', 'en'));
--rollback alter table accounts drop constraint accounts_locale_check;
--rollback alter table accounts drop column locale;

--liquibase formatted sql

-- Ödenmemiş sipariş işinin araması (add-stale-record-cleanup D1).
--
-- İş her turda ödeme süresi dolmuş PENDING_PAYMENT siparişlerini en eski
-- süreliden arar. Kısmi indeks yalnızca ödeme bekleyen satırları kapsar:
-- iş onları kapattıkça indeks küçük kalır, bütün tablo taranmaz.
-- Ekleyicidir; veri değişmez.

--changeset bahceden:016-pending-payment-index
--comment: Odeme bekleyen siparisler icin kismi indeks (odenmemis siparis isi)
create index orders_pending_payment_expiry_idx on orders (payment_expires_at, id) where status = 'PENDING_PAYMENT';
--rollback drop index if exists orders_pending_payment_expiry_idx;

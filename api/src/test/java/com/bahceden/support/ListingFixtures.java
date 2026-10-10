package com.bahceden.support;

import com.bahceden.common.security.AccessTokenIssuer;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * İlan testlerinin ortak kurulumu: hesap, satıcı kaydı, token ve temizlik
 * (AGENTS.md §11.7 küçük fabrikalar). Veriler tamamen sahtedir (K10).
 *
 * <p>Satıcı ve ilan satırları hesaplara yabancı anahtarla bağlıdır; diğer test
 * sınıfları hesapları sildiği için {@link #cleanUp()} hem önce hem sonra çağrılır.
 */
public final class ListingFixtures {

    private final JdbcTemplate jdbc;
    private final AccessTokenIssuer accessTokens;

    public ListingFixtures(JdbcTemplate jdbc, AccessTokenIssuer accessTokens) {
        this.jdbc = jdbc;
        this.accessTokens = accessTokens;
    }

    /**
     * Gönderi, sipariş, ödeme, sepet ve adres kayıtları; hesaplı satıcıların ürünleri ve
     * geçmişleri, ödeme hesapları, satıcı kayıtları ve bütün hesaplar silinir.
     */
    public void cleanUp() {
        jdbc.execute("delete from refunds");
        jdbc.execute("delete from payments");
        jdbc.execute("delete from order_status_changes");
        jdbc.execute("delete from shipments");
        jdbc.execute("delete from order_items");
        jdbc.execute("delete from seller_orders");
        jdbc.execute("delete from orders");
        jdbc.execute("delete from cart_items");
        jdbc.execute("delete from carts");
        jdbc.execute("delete from addresses");
        jdbc.execute("delete from seller_payout_accounts");
        jdbc.execute("delete from product_status_changes");
        jdbc.execute("delete from product_photos");
        jdbc.execute("delete from products where seller_id in (select id from sellers where account_id is not null)");
        jdbc.execute("delete from seller_status_changes");
        jdbc.execute("delete from seller_documents");
        jdbc.execute("delete from sellers where account_id is not null");
        jdbc.execute("delete from accounts");
    }

    /** E-postası doğrulanmış hesap; roller verilen sırayla eklenir. */
    public long account(String email, String locale, String... roles) {
        jdbc.update("""
                insert into accounts (email, password_hash, status, email_verified, locale)
                values (?, 'test-hash-kullanilmaz', 'ACTIVE', true, ?)""", email, locale);
        var id = jdbc.queryForObject("select id from accounts where email = ?", Long.class, email);
        for (var role : roles) {
            jdbc.update("insert into account_roles (account_id, role) values (?, ?)", id, role);
        }
        return id;
    }

    /** Hesaba bağlı satıcı kaydı; durum doğrudan yazılır (başvuru akışı kendi testinde). */
    public long seller(long accountId, String slug, String status) {
        jdbc.update("""
                insert into sellers (account_id, slug, display_name, city, status, phone, status_changed_at)
                values (?, ?, ?, 'Manisa', ?, '+905550000000', now())""", accountId, slug, "Satıcı " + slug, status);
        return jdbc.queryForObject("select id from sellers where account_id = ?", Long.class, accountId);
    }

    /** Onaylı satıcı hesabı ve kaydı; dönen değer hesap kimliği. */
    public long approvedSeller(String email) {
        var accountId = account(email, "tr", "ROLE_CUSTOMER", "ROLE_SELLER");
        seller(accountId, email.substring(0, email.indexOf('@')), "APPROVED");
        return accountId;
    }

    public long sellerIdOf(long accountId) {
        return jdbc.queryForObject("select id from sellers where account_id = ?", Long.class, accountId);
    }

    /** Satıcıya etkin bir ödeme hesabı (sahte, sağlaması geçerli değerler; add-cart-and-checkout D2). */
    public void activePayoutAccount(long sellerAccountId) {
        jdbc.update("""
                insert into seller_payout_accounts (seller_id, legal_type, legal_name, tax_id, iban, status,
                                                    provider_sub_merchant_key)
                values (?, 'INDIVIDUAL', 'Örnek Üretici', '10000000078', 'TR330006100519786457841326', 'ACTIVE', ?)""",
                sellerIdOf(sellerAccountId), "test-sub-seller-" + sellerAccountId);
    }

    /**
     * Doğrudan yayında bir ürün; fotoğraf yoktur (vitrin fotoğrafsız ürünü de
     * gösterir). Dönen değer ürün kimliği.
     */
    public long publishedProduct(long sellerAccountId, String slug, long priceAmount, int stock) {
        jdbc.update("""
                insert into products (seller_id, slug, name, status, unit, price_amount, price_currency,
                                      stock_quantity, published_at, status_changed_at)
                values (?, ?, ?, 'PUBLISHED', 'KILOGRAM', ?, 'TRY', ?, now(), now())""",
                sellerIdOf(sellerAccountId), slug, "Ürün " + slug, priceAmount, stock);
        return jdbc.queryForObject("select id from products where slug = ?", Long.class, slug);
    }

    /** Hesaba sahte bir teslimat adresi (add-cart-and-checkout D5); dönen değer adres kimliği. */
    public long address(long accountId, String provinceCode) {
        jdbc.update("""
                insert into addresses (account_id, title, recipient_name, phone, province_code, district, address_line)
                values (?, 'Ev', 'Örnek Alıcı', '+905550000000', ?, 'Bornova', 'Ornek Mahallesi No: 1')""",
                accountId, provinceCode);
        return jdbc.queryForObject("select max(id) from addresses where account_id = ?", Long.class, accountId);
    }

    /** Sepete satır; sepet yoksa açılır (D4). */
    public void cartItem(long accountId, long productId, int quantity) {
        jdbc.update("insert into carts (account_id) values (?) on conflict (account_id) do nothing", accountId);
        var cartId = jdbc.queryForObject("select id from carts where account_id = ?", Long.class, accountId);
        jdbc.update("insert into cart_items (cart_id, product_id, quantity) values (?, ?, ?)", cartId, productId, quantity);
    }

    public String token(long accountId, String... roles) {
        var email = jdbc.queryForObject("select email from accounts where id = ?", String.class, accountId);
        return accessTokens.issue(accountId, email, List.of(roles)).value();
    }

    public String sellerToken(long accountId) {
        return token(accountId, "ROLE_CUSTOMER", "ROLE_SELLER");
    }
}

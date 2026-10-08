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

    /** Hesaplı satıcıların ürünleri ve geçmişleri, satıcı kayıtları ve bütün hesaplar silinir. */
    public void cleanUp() {
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

    public String token(long accountId, String... roles) {
        var email = jdbc.queryForObject("select email from accounts where id = ?", String.class, accountId);
        return accessTokens.issue(accountId, email, List.of(roles)).value();
    }

    public String sellerToken(long accountId) {
        return token(accountId, "ROLE_CUSTOMER", "ROLE_SELLER");
    }
}

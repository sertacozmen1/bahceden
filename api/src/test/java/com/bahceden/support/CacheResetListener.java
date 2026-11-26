package com.bahceden.support;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Her test metodundan önce bağlamdaki bütün cache'leri boşaltır (ADR-109).
 *
 * <p>Testler veriyi çoğunlukla servisleri atlayarak, {@code JdbcTemplate} ile
 * kurar ve temizler ({@code ListingFixtures}); bu yazmalar cache'i silmez.
 * Paylaşılan test bağlamında aynı {@code slug} bir önceki testin cache'teki
 * görünümünü döndürürdü. Bu dinleyici veritabanı temizliğinin cache karşılığıdır;
 * uygulamanın kendi silme davranışı {@code StorefrontCacheTest}'te ayrıca
 * doğrulanır.
 *
 * <p>{@code META-INF/spring.factories} ile kaydedilir; bütün Spring testlerine
 * uygulanır. Bağlamda cache yöneticisi yoksa (dilim testleri) hiçbir şey yapmaz.
 */
public class CacheResetListener extends AbstractTestExecutionListener {

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        if (!testContext.hasApplicationContext()) {
            return;
        }
        var context = testContext.getApplicationContext();
        context.getBeanProvider(CacheManager.class).ifAvailable(caches ->
                caches.getCacheNames().forEach(name -> {
                    var cache = caches.getCache(name);
                    if (cache != null) {
                        clear(cache);
                    }
                }));
    }

    /** Redis'in bilerek erişilemez yapıldığı bağlamda ({@code StorefrontCacheUnavailableTest}) silinecek bir şey yoktur. */
    private static void clear(Cache cache) {
        try {
            cache.clear();
        } catch (DataAccessException unreachable) {
            return;
        }
    }
}

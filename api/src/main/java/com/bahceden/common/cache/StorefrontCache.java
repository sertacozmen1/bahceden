package com.bahceden.common.cache;

import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Vitrin cache'inin adları ve silinmesi (ADR-109).
 *
 * <p>Silme transaction <strong>başarıyla bittikten sonra</strong> yapılır: geri
 * alınan bir değişiklik cache'i boşuna silmez, commit öncesi bir okuma da eski
 * değeri silmeden sonra yeniden yazamaz. Bu desen {@code OrderNotifier} ile
 * aynıdır. Transaction yoksa hemen silinir.
 *
 * <p>Ürüne özgü değişiklikte o ürünün detayı ve bütün liste sayfaları silinir;
 * satıcı düzeyindeki değişiklikte (onay durumu, ödeme hesabı) iki cache de
 * tümden temizlenir. Bu olaylar seyrektir ve satıcının ürün adreslerini
 * toplamaktan basittir.
 *
 * <p>Redis hatası işlemi geri almaz: commit zaten olmuştur. {@code WARN} yazılır;
 * eski görünüm en çok cache süresi kadar kalır.
 */
@Component
public class StorefrontCache {

    /** Ürün detayı; anahtar {@code slug}. */
    public static final String PRODUCT = "storefront-product";

    /** Vitrin listesinin ilk sayfaları; anahtar {@code sayfa:boyut}. */
    public static final String LIST = "storefront-list";

    private static final Logger log = LoggerFactory.getLogger(StorefrontCache.class);

    private final CacheManager caches;

    StorefrontCache(CacheManager caches) {
        this.caches = caches;
    }

    /** Ürünlerin detayını ve vitrin listesini commit sonrası siler. */
    public void evictProducts(Collection<String> slugs) {
        var copy = List.copyOf(slugs);
        afterCommit(() -> {
            copy.forEach(slug -> run(() -> cache(PRODUCT).evict(slug), PRODUCT));
            run(() -> cache(LIST).clear(), LIST);
        });
    }

    /** Satıcı düzeyindeki değişiklik: iki cache commit sonrası tümden temizlenir. */
    public void evictAll() {
        afterCommit(() -> {
            run(() -> cache(PRODUCT).clear(), PRODUCT);
            run(() -> cache(LIST).clear(), LIST);
        });
    }

    private org.springframework.cache.Cache cache(String name) {
        var cache = caches.getCache(name);
        if (cache == null) {
            throw new IllegalStateException("Tanımsız cache: " + name);
        }
        return cache;
    }

    private static void run(Runnable eviction, String name) {
        try {
            eviction.run();
        } catch (DataAccessException unavailable) {
            log.warn("Vitrin cache'i silinemedi ({}); eski görünüm en çok cache süresi kadar kalır: {}", name,
                    unavailable.getClass().getSimpleName());
        }
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}

package com.bahceden.catalog;

import com.bahceden.catalog.dto.ProductDetail;
import com.bahceden.catalog.dto.ProductSummary;
import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.cache.StorefrontCache;
import com.bahceden.common.web.PageResponse;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Vitrin okuma cache'i: Spring Cache + Redis (ADR-109).
 *
 * <p>Yalnızca iki cache vardır ve ikisi de herkese açık, dilden bağımsız vitrin
 * yanıtıdır ({@link StorefrontCache#PRODUCT}, {@link StorefrontCache#LIST}).
 * Tanımsız bir cache adı açılışta değil ilk kullanımda hata verir
 * ({@code disableCreateOnMissingCache}): hesap, sepet veya sipariş yanıtı
 * yanlışlıkla cache'lenemez (AGENTS.md §3 istisnası, ADR-107).
 *
 * <p>Değerler tip bilgisi olmadan, cache başına sabit tiple JSON yazılır: Redis'te
 * sınıf adı durmaz ve JDK'nın değiştirilemez listeleri sorunsuz okunur.
 *
 * <p><strong>Fail-open:</strong> Redis hatası okumayı kırmaz; cache atlanır ve
 * değer veritabanından okunur.
 */
@Configuration
@EnableCaching
class StorefrontCacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(StorefrontCacheConfig.class);

    /** Silmede {@code KEYS} yerine {@code SCAN}: Redis'i bloklamaz. */
    private static final int SCAN_BATCH = 1000;

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connections, ObjectMapper objectMapper,
                                   BahcedenProperties properties) {
        var mapper = objectMapper.copy();
        JavaType listType = mapper.getTypeFactory().constructParametricType(PageResponse.class, ProductSummary.class);
        var base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.cache().storefrontTtl())
                .disableCachingNullValues()
                .computePrefixWith(name -> "cache:" + name + ":");
        return RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(connections, BatchStrategies.scan(SCAN_BATCH)))
                .cacheDefaults(base)
                .withInitialCacheConfigurations(Map.of(
                        StorefrontCache.PRODUCT, base.serializeValuesWith(SerializationPair.fromSerializer(
                                new Jackson2JsonRedisSerializer<>(mapper, ProductDetail.class))),
                        StorefrontCache.LIST, base.serializeValuesWith(SerializationPair.fromSerializer(
                                new Jackson2JsonRedisSerializer<>(mapper, listType)))))
                .disableCreateOnMissingCache()
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                warn("okunamadı", cache, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                warn("yazılamadı", cache, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                warn("silinemedi", cache, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                warn("temizlenemedi", cache, exception);
            }
        };
    }

    private static void warn(String what, Cache cache, RuntimeException exception) {
        log.warn("Vitrin cache'i {} ({}); veritabanıyla devam ediliyor: {}", what, cache.getName(),
                exception.getClass().getSimpleName());
    }
}

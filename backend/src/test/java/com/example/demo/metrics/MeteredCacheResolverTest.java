package com.example.demo.metrics;

import com.example.demo.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheableOperation;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeteredCacheResolverTest {

    private static final String CACHE = "football-data-competition-teams";

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(CACHE);
    private final MeteredCacheResolver resolver = new MeteredCacheResolver(cacheManager,
            new BusinessMetrics(meterRegistry, mock(UserRepository.class)));

    @Test
    void secondLookupWithoutRunningTheLoaderIsCountedAsCache() {
        Cache cache = resolve(CACHE);
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.get("PL", () -> "teams-" + loads.incrementAndGet())).isEqualTo("teams-1");
        assertThat(cacheHits()).isZero();

        assertThat(cache.get("PL", () -> "teams-" + loads.incrementAndGet())).isEqualTo("teams-1");
        assertThat(loads).hasValue(1);
        assertThat(cacheHits()).isEqualTo(1);
    }

    @Test
    void plainLookupsCountOnlyPresentValues() {
        Cache cache = resolve(CACHE);

        assertThat(cache.get("PD")).isNull();
        assertThat(cacheHits()).isZero();

        cache.put("PD", "teams");
        assertThat(cache.get("PD", String.class)).isEqualTo("teams");
        assertThat(cacheHits()).isEqualTo(1);
    }

    @Test
    void otherOperationsDelegateToTheRealCache() {
        Cache cache = resolve(CACHE);
        Cache real = cacheManager.getCache(CACHE);

        assertThat(cache.getName()).isEqualTo(CACHE);
        assertThat(cache.getNativeCache()).isSameAs(real.getNativeCache());
        cache.put("SA", "a");
        assertThat(real.get("SA", String.class)).isEqualTo("a");
        assertThat(cache.putIfAbsent("SA", "b").get()).isEqualTo("a");
        cache.evict("SA");
        assertThat(real.get("SA")).isNull();
        cache.put("BL1", "c");
        assertThat(cache.evictIfPresent("BL1")).isTrue();
        cache.put("FL1", "d");
        cache.clear();
        assertThat(real.get("FL1")).isNull();
        assertThat(cacheHits()).isZero();
    }

    @Test
    void unknownCacheFailsLikeTheDefaultResolver() {
        ConcurrentMapCacheManager fixed = new ConcurrentMapCacheManager(CACHE);
        MeteredCacheResolver strict = new MeteredCacheResolver(fixed,
                new BusinessMetrics(new SimpleMeterRegistry(), mock(UserRepository.class)));

        assertThatThrownBy(() -> strict.resolveCaches(context("missing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    private Cache resolve(String name) {
        Collection<? extends Cache> caches = resolver.resolveCaches(context(name));
        assertThat(caches).hasSize(1);
        return caches.iterator().next();
    }

    private static CacheOperationInvocationContext<?> context(String name) {
        CacheableOperation.Builder builder = new CacheableOperation.Builder();
        builder.setName("fetchCompetitionTeams");
        builder.setCacheNames(name);
        CacheableOperation operation = builder.build();
        @SuppressWarnings("unchecked")
        CacheOperationInvocationContext<CacheableOperation> context = mock(CacheOperationInvocationContext.class);
        when(context.getOperation()).thenReturn(operation);
        return context;
    }

    private double cacheHits() {
        return meterRegistry.get("gdmarket.external.calls").tags("source", "football_data", "outcome", "cache")
                .counter().count();
    }
}

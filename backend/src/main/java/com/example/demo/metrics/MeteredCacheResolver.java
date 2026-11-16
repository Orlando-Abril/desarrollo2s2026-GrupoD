package com.example.demo.metrics;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Resolvedor de caché de Football-Data (spec 036) que cuenta como {@code cache} cada consulta resuelta sin llamar a la
 * fuente. Usa el {@link CacheManager} existente y sólo envuelve la caché que devuelve: no cambia qué se guarda ni cuándo.
 */
@Component(MeteredCacheResolver.FOOTBALL_DATA)
public class MeteredCacheResolver implements CacheResolver {

    public static final String FOOTBALL_DATA = "footballDataCacheResolver";

    private final CacheManager cacheManager;
    private final BusinessMetrics metrics;

    public MeteredCacheResolver(CacheManager cacheManager, BusinessMetrics metrics) {
        this.cacheManager = cacheManager;
        this.metrics = metrics;
    }

    @Override
    @NonNull
    public Collection<? extends Cache> resolveCaches(@NonNull CacheOperationInvocationContext<?> context) {
        return context.getOperation().getCacheNames().stream()
                .map(name -> {
                    Cache cache = cacheManager.getCache(name);
                    if (cache == null) {
                        throw new IllegalArgumentException("Cannot find cache named '" + name + "' for "
                                + context.getOperation());
                    }
                    return (Cache) new CountingCache(cache, metrics);
                })
                .toList();
    }

    /** Delega todo en la caché real; sólo cuenta los aciertos. */
    private static final class CountingCache implements Cache {

        private final Cache delegate;
        private final BusinessMetrics metrics;

        private CountingCache(Cache delegate, BusinessMetrics metrics) {
            this.delegate = delegate;
            this.metrics = metrics;
        }

        @Override
        @NonNull
        public String getName() {
            return delegate.getName();
        }

        @Override
        @NonNull
        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        @Override
        public ValueWrapper get(@NonNull Object key) {
            return countHit(delegate.get(key));
        }

        @Override
        public <T> T get(@NonNull Object key, Class<T> type) {
            return countHit(delegate.get(key, type));
        }

        @Override
        public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
            AtomicBoolean loaded = new AtomicBoolean(false);
            T value = delegate.get(key, () -> {
                loaded.set(true);
                return valueLoader.call();
            });
            if (!loaded.get()) {
                hit();
            }
            return value;
        }

        @Override
        public CompletableFuture<?> retrieve(@NonNull Object key) {
            return delegate.retrieve(key);
        }

        @Override
        @NonNull
        public <T> CompletableFuture<T> retrieve(@NonNull Object key,
                                                 @NonNull Supplier<CompletableFuture<T>> valueLoader) {
            return delegate.retrieve(key, valueLoader);
        }

        @Override
        public void put(@NonNull Object key, Object value) {
            delegate.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(@NonNull Object key, Object value) {
            return delegate.putIfAbsent(key, value);
        }

        @Override
        public void evict(@NonNull Object key) {
            delegate.evict(key);
        }

        @Override
        public boolean evictIfPresent(@NonNull Object key) {
            return delegate.evictIfPresent(key);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public boolean invalidate() {
            return delegate.invalidate();
        }

        private <T> T countHit(T value) {
            if (value != null) {
                hit();
            }
            return value;
        }

        private void hit() {
            metrics.externalCall(ExternalSource.FOOTBALL_DATA, ExternalCallOutcome.CACHE);
        }
    }
}

package com.baichen.rpc.fallback;

import com.baichen.rpc.exception.RpcException;
import com.baichen.rpc.metrics.MetricsData;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CacheFallback implements Fallback {
    private static final Object NULL = new Object();

    private final Map<CacheKey, Object>  resultCache = new ConcurrentHashMap<>();

    @Override
    public Object fallback(MetricsData metricsData) {
        CacheKey cacheKey = new CacheKey(metricsData.getMethod(), metricsData.getArgs());
        Object result = resultCache.get(cacheKey);
        if (result == NULL) {
            return null;
        }
        if (result == null) {
            log.warn("缓存未命中，方法：{}，参数：{}", metricsData.getMethod().getName(), Arrays.toString(metricsData.getArgs()));
            throw new RpcException("缓存未命中");
        }
        return result;
    }

    @Override
    public void recordMetrics(MetricsData metricsData) {
        CacheKey cacheKey = new CacheKey(metricsData.getMethod(), metricsData.getArgs());
        Object result = metricsData.getResult();
        if (result == null) {
            result = NULL;
        }
        resultCache.put(cacheKey, result);
    }

    @Data
    private static class CacheKey {
        private Method method;
        private Object[] args;

        public CacheKey(Method method, Object[] args) {
            this.method = method;
            this.args = args;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return Objects.equals(method, cacheKey.method) && Objects.deepEquals(args, cacheKey.args);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, Arrays.hashCode(args));
        }
    }
}

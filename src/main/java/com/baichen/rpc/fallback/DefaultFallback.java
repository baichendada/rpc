package com.baichen.rpc.fallback;

import com.baichen.rpc.metrics.MetricsData;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public class DefaultFallback implements Fallback {

    private final Fallback cacheFallback;

    private final Fallback mockFallback;

    public DefaultFallback(Fallback cacheFallback, Fallback mockFallback) {
        this.cacheFallback = cacheFallback;
        this.mockFallback = mockFallback;
    }


    @Override
    public Object fallback(MetricsData metricsData) throws Exception {
        try {
            return cacheFallback.fallback(metricsData);
        } catch (Exception e) {
            log.warn("缓存未命中，方法：{}，参数：{}", metricsData.getMethod().getName(), Arrays.toString(metricsData.getArgs()));
        }
        return mockFallback.fallback(metricsData);
    }

    @Override
    public void recordMetrics(MetricsData metricsData) {
        cacheFallback.recordMetrics(metricsData);
        mockFallback.recordMetrics(metricsData);
    }
}

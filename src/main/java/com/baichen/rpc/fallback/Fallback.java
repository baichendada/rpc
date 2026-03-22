package com.baichen.rpc.fallback;

import com.baichen.rpc.metrics.MetricsData;

public interface Fallback {

    Object fallback(MetricsData metricsData) throws Exception;

    void recordMetrics(MetricsData metricsData);
}

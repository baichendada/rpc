package com.baichen.rpc.breaker;

import com.baichen.metrics.MetricsData;

public interface CircuitBreaker {

    boolean allowRequest();

    void recordRpc(MetricsData metricsData);

    enum State {
        CLOSED, OPEN, HALF_OPEN
    }
}

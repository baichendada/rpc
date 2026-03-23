package com.baichen.rpc.breaker;

import com.baichen.rpc.spi.SpiTag;

@SpiTag("responseTime")
public class ResponseTimeCircuitBreakerFactory implements CircuitBreakerFactory {

    @Override
    public CircuitBreaker create(long slowRequestThresholdMs, double slowRequestRatioThreshold) {
        return new ResponseTimeCircuitBreaker(slowRequestThresholdMs, slowRequestRatioThreshold);
    }
}

package com.baichen.rpc.breaker;

import com.baichen.rpc.consumer.ConsumerProperties;
import com.baichen.rpc.registry.ServiceMateData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CircuitBreakerManager {

    private final Map<ServiceMateData, CircuitBreaker> breakerMap = new ConcurrentHashMap<>();

    private final ConsumerProperties consumerProperties;

    public CircuitBreakerManager(ConsumerProperties consumerProperties) {
        this.consumerProperties = consumerProperties;
    }

    public CircuitBreaker getOrCreateBreaker(ServiceMateData serviceMateData) {
        return breakerMap.computeIfAbsent(serviceMateData, k -> createBreaker());
    }

    private CircuitBreaker createBreaker() {
        return new ResponseTimeCircuitBreaker(consumerProperties.getSlowRequestThresholdMs(), consumerProperties.getSlowRequestRate());
    }
}

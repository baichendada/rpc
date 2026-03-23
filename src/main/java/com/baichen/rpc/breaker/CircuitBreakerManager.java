package com.baichen.rpc.breaker;

import com.baichen.rpc.consumer.ConsumerProperties;
import com.baichen.rpc.registry.ServiceMateData;
import com.baichen.rpc.spi.SpiTag;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CircuitBreakerManager {

    private final Map<ServiceMateData, CircuitBreaker> breakerMap = new ConcurrentHashMap<>();

    private final ConsumerProperties consumerProperties;

    private final Map<String, CircuitBreakerFactory> factories = new HashMap<>();

    public CircuitBreakerManager(ConsumerProperties consumerProperties) {
        this.consumerProperties = consumerProperties;
        loadFactories();
    }

    private void loadFactories() {
        ServiceLoader<CircuitBreakerFactory> loader = ServiceLoader.load(CircuitBreakerFactory.class);
        for (CircuitBreakerFactory factory : loader) {
            SpiTag annotation = factory.getClass().getAnnotation(SpiTag.class);
            if (annotation == null) {
                log.warn("CircuitBreakerFactory {} does not have SpiTag annotation, skipping", factory.getClass().getName());
                continue;
            }
            String name = annotation.value().toLowerCase();
            if (factories.put(name, factory) != null) {
                throw new IllegalArgumentException("Duplicate CircuitBreakerFactory name: " + name);
            }
        }
    }

    public CircuitBreaker getOrCreateBreaker(ServiceMateData serviceMateData) {
        return breakerMap.computeIfAbsent(serviceMateData, k -> createBreaker());
    }

    private CircuitBreaker createBreaker() {
        String breakerType = consumerProperties.getCircuitBreakerType();
        CircuitBreakerFactory factory = factories.get(breakerType.toLowerCase());
        if (factory == null) {
            throw new IllegalArgumentException("Unsupported circuit breaker type: " + breakerType);
        }
        return factory.create(
                consumerProperties.getSlowRequestThresholdMs(),
                consumerProperties.getSlowRequestRate()
        );
    }
}

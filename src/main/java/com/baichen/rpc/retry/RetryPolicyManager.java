package com.baichen.rpc.retry;

import com.baichen.rpc.spi.SpiTag;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

@Slf4j
public class RetryPolicyManager {

    private final Map<String, RetryPolicy> retryPolicies = new HashMap<>();

    public RetryPolicyManager() {
        ServiceLoader<RetryPolicy> loader = ServiceLoader.load(RetryPolicy.class);
        for (RetryPolicy policy : loader) {
            SpiTag annotation = policy.getClass().getAnnotation(SpiTag.class);
            if (annotation == null) {
                log.warn("RetryPolicy {} does not have SpiTag annotation, skipping", policy.getClass().getName());
                continue;
            }
            String name = annotation.value().toUpperCase();
            if (retryPolicies.put(name, policy) != null) {
                throw new IllegalArgumentException("Duplicate RetryPolicy name: " + name);
            }
        }
    }

    public RetryPolicy getRetryPolicy(String name) {
        return retryPolicies.get(name.toUpperCase());
    }
}

package com.baichen.rpc.breaker;

/**
 * 熔断器工厂接口
 * 用于创建不同类型的熔断器实例
 */
public interface CircuitBreakerFactory {

    /**
     * 创建熔断器实例
     *
     * @param slowRequestThresholdMs 慢请求阈值（毫秒）
     * @param slowRequestRatioThreshold 慢请求比例阈值
     * @return 熔断器实例
     */
    CircuitBreaker create(long slowRequestThresholdMs, double slowRequestRatioThreshold);
}

package com.baichen.rpc.fallback;

import com.baichen.rpc.metrics.MetricsData;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockFallback implements Fallback {
    private final Map<Class<?>, Object> invokerCache = new ConcurrentHashMap<>();

    @Override
    public Object fallback(MetricsData metricsData) throws Exception {
        Method method = metricsData.getMethod();
        Class<?> declaringClass = method.getDeclaringClass();
        FallbackTag annotation = declaringClass.getAnnotation(FallbackTag.class);
        if (annotation == null) {
            throw new RuntimeException("未找到消费者降级方法实现");
        }

        Class<?> value = annotation.value();
        if (!declaringClass.isAssignableFrom(value)) {
            throw new RuntimeException("消费者降级方法实现不匹配");
        }
        Object invoker = invokerCache.computeIfAbsent(value, this::createMockInvoker);
        return method.invoke(invoker, metricsData.getArgs());
    }

    private Object createMockInvoker(Class<?> value) {
        try {
            return value.getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("创建消费者降级方法实例失败", e);
        }
    }

    @Override
    public void recordMetrics(MetricsData metricsData) {
    }
}

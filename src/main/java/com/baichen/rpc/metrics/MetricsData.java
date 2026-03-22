package com.baichen.rpc.metrics;

import com.baichen.rpc.registry.ServiceMateData;
import lombok.Data;

import java.lang.reflect.Method;

@Data
public class MetricsData {

    private boolean success;
    private Throwable t;
    private long duration;
    private long startTime;

    private Method method;
    private Object[] args;
    private Object result;
    private ServiceMateData providerService;

    public static MetricsData create(Method method, Object[] args, ServiceMateData providerService) {
        MetricsData metricsData = new MetricsData();
        metricsData.startTime = System.currentTimeMillis();
        metricsData.method = method;
        metricsData.args = args;
        metricsData.providerService = providerService;
        return metricsData;
    }

    public void complete(Object result) {
        this.success = true;
        this.duration = System.currentTimeMillis() - startTime;
        this.result = result;
    }

    public void completeWithException(Throwable t) {
        this.t = t;
        this.duration = System.currentTimeMillis() - startTime;
    }
}

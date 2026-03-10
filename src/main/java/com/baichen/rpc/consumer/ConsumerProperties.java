package com.baichen.rpc.consumer;

import com.baichen.rpc.registry.ServiceRegistryConfig;
import lombok.Data;

@Data
public class ConsumerProperties {
    private Integer workerThreadNum = 4;
    private Integer connectTimeoutMillis = 5000;
    private Integer waitResponseTimeoutMillis = 5000;
    private ServiceRegistryConfig serviceRegistryConfig;
}

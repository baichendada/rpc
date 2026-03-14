package com.baichen.rpc.consumer;

import com.baichen.rpc.registry.ServiceRegistryConfig;
import lombok.Data;

@Data
public class ConsumerProperties {
    private Integer workerThreadNum = 4;
    private Integer connectTimeoutMs = 5000;
    private Long waitResponseTimeoutMs = 3000L;
    private Long totalTimeoutMs = 10000L;
    private String loadBalancePolicy = "roundRobin";
    private String retryPolicy = "forkAll";
    private ServiceRegistryConfig serviceRegistryConfig;
}

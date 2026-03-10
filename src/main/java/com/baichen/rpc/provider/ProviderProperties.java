package com.baichen.rpc.provider;

import com.baichen.rpc.registry.ServiceRegistryConfig;
import lombok.Data;

@Data
public class ProviderProperties {
    private String host;
    private Integer port;
    private Integer workerThreadNum = 4;
    private ServiceRegistryConfig serviceRegistryConfig;
}

package com.baichen.rpc.provider;

import com.baichen.rpc.registry.ServiceRegistryConfig;
import lombok.Data;

@Data
public class ProviderProperties {
    private String host;
    private Integer port;
    private Integer workerThreadNum = 4;
    private Integer globalLimit = 100;
    private Integer serviceLimit = 3;
    private String serializerType = "json";
    private String compressorType = "none";
    private ServiceRegistryConfig serviceRegistryConfig;
}

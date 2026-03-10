package com.baichen.rpc.registry;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class ServiceRegistryConfig {

    private String registryType = "zookeeper";

    private String connectString;
}

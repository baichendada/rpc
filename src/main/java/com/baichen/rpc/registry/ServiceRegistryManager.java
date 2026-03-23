package com.baichen.rpc.registry;

import com.baichen.rpc.spi.SpiTag;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

@Slf4j
public class ServiceRegistryManager {

    private final Map<String, ServiceRegistry> registries = new HashMap<>();

    public ServiceRegistryManager() {
        ServiceLoader<ServiceRegistry> loader = ServiceLoader.load(ServiceRegistry.class);
        for (ServiceRegistry registry : loader) {
            SpiTag annotation = registry.getClass().getAnnotation(SpiTag.class);
            if (annotation == null) {
                log.warn("ServiceRegistry {} does not have SpiTag annotation, skipping", registry.getClass().getName());
                continue;
            }
            String name = annotation.value().toLowerCase();
            if (registries.put(name, registry) != null) {
                throw new IllegalArgumentException("Duplicate ServiceRegistry name: " + name);
            }
        }
    }

    public ServiceRegistry getServiceRegistry(String type) {
        ServiceRegistry registry = registries.get(type.toLowerCase());
        if (registry == null) {
            throw new IllegalArgumentException("Unsupported registry type: " + type);
        }
        return registry;
    }
}

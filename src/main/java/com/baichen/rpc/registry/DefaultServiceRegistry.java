package com.baichen.rpc.registry;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class DefaultServiceRegistry implements ServiceRegistry {

    private final ServiceRegistry delegate;

    private Map<String, List<ServiceMateData>> serviceCache = new ConcurrentHashMap<>();

    public DefaultServiceRegistry(ServiceRegistryConfig config) {
        this.delegate = createServiceRegistry(config);
    }

    @Override
    public void init(ServiceRegistryConfig config) throws Exception {
        log.info("{} registry init with config: {}", delegate.getClass().getSimpleName(), config);
        delegate.init(config);
    }

    @Override
    public void registry(ServiceMateData service) throws Exception {
        delegate.registry(service);
    }

    @Override
    public List<ServiceMateData> fetchSeviceList(String serviceName) {
        try {
            List<ServiceMateData> serviceMateData = delegate.fetchSeviceList(serviceName);
            serviceCache.put(serviceName, serviceMateData);
            return serviceMateData;
        } catch (Exception e) {
            return serviceCache.getOrDefault(serviceName, new ArrayList<>());
        }
    }

    private ServiceRegistry createServiceRegistry(ServiceRegistryConfig config) {
        if ("zookeeper".equalsIgnoreCase(config.getRegistryType())) {
            return new ZookeeperServiceRegistry();
        } else if ("redis".equalsIgnoreCase(config.getRegistryType())) {
            return new RedisServiceRegistry();
        }
        throw new IllegalArgumentException("Unsupported registry type: " + config.getRegistryType());
    }
}

package com.baichen.rpc.register;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class DefaultServiceRegister implements ServiceRegister {

    private final ServiceRegister delegate;

    private Map<String, List<ServiceMateData>> serviceCache = new ConcurrentHashMap<>();

    public DefaultServiceRegister(ServiceRegisterConfig config) {
        this.delegate = createServiceRegister(config);
    }

    @Override
    public void init(ServiceRegisterConfig config) throws Exception {
        log.info("{} register init with config: {}", delegate.getClass().getSimpleName(), config);
        delegate.init(config);
    }

    @Override
    public void register(ServiceMateData service) throws Exception {
        delegate.register(service);
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

    private ServiceRegister createServiceRegister(ServiceRegisterConfig config) {
        if ("zookeeper".equalsIgnoreCase(config.getRegisterType())) {
            return new ZookeeperServiceRegister();
        } else if ("redis".equalsIgnoreCase(config.getRegisterType())) {
            return new RedisServiceRegister();
        }
        throw new IllegalArgumentException("Unsupported register type: " + config.getRegisterType());
    }
}

package com.baichen.rpc.registry;

import java.util.List;

public interface ServiceRegistry {

    void init(ServiceRegistryConfig config) throws Exception;

    void registry(ServiceMateData service) throws Exception;

    List<ServiceMateData> fetchSeviceList(String serviceName) throws Exception;
}

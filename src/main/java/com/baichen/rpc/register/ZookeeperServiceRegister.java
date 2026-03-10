package com.baichen.rpc.register;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;

import java.util.List;

public class ZookeeperServiceRegister implements ServiceRegister {

    private static final String BASE_PATH = "/rpc";

    private CuratorFramework client;

    private ServiceDiscovery<ServiceMateData> serviceDiscovery;

    @Override
    public void init(ServiceRegisterConfig config) throws Exception {
        client = CuratorFrameworkFactory.builder()
                .connectString(config.getConnectString())
                .sessionTimeoutMs(60000)
                .connectionTimeoutMs(3000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();

        serviceDiscovery = ServiceDiscoveryBuilder.builder(ServiceMateData.class)
                .client(client)
                .basePath(BASE_PATH)
                .serializer(new JsonInstanceSerializer<>(ServiceMateData.class))
                .build();
        serviceDiscovery.start();
    }

    @Override
    public void register(ServiceMateData mateData) throws Exception {
        ServiceInstance<ServiceMateData> serviceInstance = ServiceInstance.<ServiceMateData>builder()
                .address(mateData.getServiceName())
                .port(mateData.getPort())
                .name(mateData.getServiceName())
                .payload(mateData)
                .build();
        serviceDiscovery.registerService(serviceInstance);
    }

    @Override
    public List<ServiceMateData> fetchSeviceList(String serviceName) throws Exception{
        return serviceDiscovery.queryForInstances(serviceName).stream().map(ServiceInstance::getPayload).toList();
    }
}

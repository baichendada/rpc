package com.baichen.rpc.provider;

import com.baichen.rpc.api.Add;
import com.baichen.rpc.registry.ServiceRegistryConfig;

/**
 * RPC 服务端启动入口
 */
public class ProviderApp {
    public static void main(String[] args) throws Exception {
        // 启动 RPC 服务端，监听 8085 端口
        ServiceRegistryConfig serviceRegistryConfig = new ServiceRegistryConfig();
        serviceRegistryConfig.setRegistryType("zookeeper");
        serviceRegistryConfig.setConnectString("127.0.0.1:2181");
        ProviderProperties providerProperties = new ProviderProperties();
        providerProperties.setServiceRegistryConfig(serviceRegistryConfig);
        providerProperties.setHost("127.0.0.1");
        providerProperties.setPort(9090);
        ProviderServer providerServer = new ProviderServer(providerProperties);
        providerServer.register(Add.class, new AddImpl());
        providerServer.start();
    }
}

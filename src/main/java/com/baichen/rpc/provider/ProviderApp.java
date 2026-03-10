package com.baichen.rpc.provider;

import com.baichen.rpc.api.Add;
import com.baichen.rpc.register.ServiceRegisterConfig;

/**
 * RPC 服务端启动入口
 */
public class ProviderApp {
    public static void main(String[] args) throws Exception {
        // 启动 RPC 服务端，监听 8085 端口
        ServiceRegisterConfig serviceRegisterConfig = new ServiceRegisterConfig();
        serviceRegisterConfig.setRegisterType("zookeeper");
        serviceRegisterConfig.setConnectString("127.0.0.1:2181");
        ProviderServer providerServer = new ProviderServer("127.0.0.1", 9091, serviceRegisterConfig);
        providerServer.register(Add.class, new AddImpl());
        providerServer.start();
    }
}

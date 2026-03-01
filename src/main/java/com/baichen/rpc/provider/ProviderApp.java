package com.baichen.rpc.provider;

/**
 * RPC 服务端启动入口
 */
public class ProviderApp {
    public static void main(String[] args) {
        // 启动 RPC 服务端，监听 8085 端口
        new ProviderServer(8085).start();
    }
}

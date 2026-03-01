package com.baichen.rpc.consumer;

import com.baichen.rpc.api.Add;

/**
 * RPC 客户端启动入口
 */
public class ConsumerApp {
    public static void main(String[] args) throws Exception {
        Add consumer = new Consumer();

        // 发起 RPC 调用
        System.out.println("调用 add(1, 3) = " + consumer.add(1, 3));
        System.out.println("调用 add(12, 3) = " + consumer.add(12, 3));
    }
}

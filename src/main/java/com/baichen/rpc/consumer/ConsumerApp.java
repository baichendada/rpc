package com.baichen.rpc.consumer;

import com.baichen.rpc.api.Add;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC 客户端启动入口
 */
@Slf4j
public class ConsumerApp {
    public static void main(String[] args) throws Exception {
//        Add consumer = new Consumer();
        ConsumerProxyFactory consumerProxyFactory = new ConsumerProxyFactory();
        for (int i = 0; i < 10; i++) {
            Add consumer = consumerProxyFactory.createConsumerProxy(Add.class);
            // 发起 RPC 调用
            log.info("调用 add(1, 3) = {}", consumer.add(1, 3));
            log.info("调用 add(12, 3) = {}", consumer.add(12, 3));
        }
    }
}

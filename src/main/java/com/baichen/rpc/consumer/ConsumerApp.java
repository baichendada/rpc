package com.baichen.rpc.consumer;

import com.baichen.rpc.api.Add;
import com.baichen.rpc.registry.ServiceRegistryConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CyclicBarrier;

/**
 * RPC 客户端启动入口
 */
@Slf4j
public class ConsumerApp {
    public static void main(String[] args) throws Exception {
//        Add consumer = new Consumer();
        ServiceRegistryConfig serviceRegistryConfig = new ServiceRegistryConfig();
        serviceRegistryConfig.setRegistryType("zookeeper");
        serviceRegistryConfig.setConnectString("127.0.0.1:2181");
        ConsumerProperties consumerProperties = new ConsumerProperties();
        consumerProperties.setServiceRegistryConfig(serviceRegistryConfig);
        consumerProperties.setGlobalLimit(100);
        consumerProperties.setServiceLimit(30);
        ConsumerProxyFactory consumerProxyFactory = new ConsumerProxyFactory(consumerProperties);
//        for (int i = 0; i < 10; i++) {
//            Add consumer = consumerProxyFactory.createConsumerProxy(Add.class);
//            // 发起 RPC 调用
//            log.info("调用 add(1, 3) = {}", consumer.add(1, 3));
//            log.info("调用 add(12, 3) = {}", consumer.add(12, 3));
//        }
//        while (true) {
//            try {
//                Add consumer = consumerProxyFactory.createConsumerProxy(Add.class);
//                // 发起 RPC 调用
//                log.info("调用 add(1, 3) = {}", consumer.add(1, 3));
////                log.info("调用 add(12, 3) = {}", consumer.add(12, 3));
//                Thread.sleep(1000);
//            } catch (Exception e) {
//                log.error("RPC 调用失败", e);
//            }
//
//        }
        Add consumer = consumerProxyFactory.createConsumerProxy(Add.class);
        // 发起 RPC 调用
//        log.info("调用 add(1, 3) = {}", consumer.add(1, 3));
//                log.info("调用 add(12, 3) = {}", consumer.add(12, 3));
//        CyclicBarrier cyclicBarrier = new CyclicBarrier(10);
//        for (int i = 0; i < 10; i++) {
//            new Thread(() -> {
//                try {
//                    cyclicBarrier.await();
//                    log.info("调用 add(1, 3) = {}", consumer.add(1, 3));
//                } catch (Exception e) {
//                    log.error("RPC 调用失败", e);
//                }
//            }).start();
//        }
        while (true) {
            try {
                consumer.add(1, 3);
            } catch (Exception e) {
                log.error("RPC 调用失败", e);
            }
            Thread.sleep(300);
        }
    }
}

package com.baichen.rpc.consumer;

import com.baichen.rpc.api.Add;
import com.baichen.rpc.api.User;
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
        consumerProxyFactory.createConsumerProxy(Add.class);
        Add consumer = consumerProxyFactory.createConsumerProxy(Add.class);
        System.out.println(consumer.add(1, 3));

        GenericConsumer genericConsumer = consumerProxyFactory.createConsumerProxy(GenericConsumer.class);
        System.out.println(genericConsumer.$invoke(Add.class.getName(), "add", new String[]{"int", "int"}, new Object[]{1, 3}));

        User user1 = new User();
        user1.setAge(1);
        user1.setName("bai");
        User user2 = new User();
        user2.setAge(3);
        user2.setName("chen");
        genericConsumer.$invoke(Add.class.getName(), "merge", new String[]{User.class.getName(), User.class.getName()}, new Object[]{user1, user2});
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
//        while (true) {
//            try {
//                consumer.add(1, 3);
//            } catch (Exception e) {
//                log.error("RPC 调用失败", e);
//            }
//            Thread.sleep(300);
//        }

//        new Thread(() -> {
//            while (true) {
//                try {
//                    log.info("调用 minus(1, 3) = {}", consumer.minus(3, 1));
//                } catch (Exception e) {
//                    log.error("RPC 调用失败", e);
//                }
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    log.error("线程休眠被中断", e);
//                }
//            }
//        });
//
//        for (int i = 0; i < 10; i++) {
//            new Thread(() -> {
//                while (true) {
//                    try {
//                        log.info("调用 add(1, 3) = {}", consumer.add(3, 1));
//                    } catch (Exception e) {
//                        log.error("RPC 调用失败", e);
//                    }
//                    try {
//                        Thread.sleep(1000);
//                    } catch (InterruptedException e) {
//                        log.error("线程休眠被中断", e);
//                    }
//                }
//            });
//        }

    }
}

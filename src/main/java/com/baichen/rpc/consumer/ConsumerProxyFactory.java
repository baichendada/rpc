package com.baichen.rpc.consumer;

import com.baichen.rpc.codec.MessageDecoder;
import com.baichen.rpc.codec.RequestEncoder;
import com.baichen.rpc.exception.RpcException;
import com.baichen.rpc.loaderbalance.LoaderBalancer;
import com.baichen.rpc.loaderbalance.RandomLoaderBalancer;
import com.baichen.rpc.loaderbalance.RoundRobinLoaderBalancer;
import com.baichen.rpc.message.Request;
import com.baichen.rpc.message.Response;
import com.baichen.rpc.registry.DefaultServiceRegistry;
import com.baichen.rpc.registry.ServiceMateData;
import com.baichen.rpc.registry.ServiceRegistry;
import com.baichen.rpc.retry.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * ConsumerProxyFactory 负责创建 RPC 客户端的动态代理对象
 * 通过 Java 的动态代理机制，可以避免接口类中大量的重复代码，简化 RPC 调用的实现
 */
@Slf4j
public class ConsumerProxyFactory {

    private ConnectionManager connectionManager;

    private final ServiceRegistry serviceRegistry;

    private final ConsumerProperties properties;

    private final LoaderBalancer balancer;

    private final RetryPolicy retryPolicy;

    private final InFlightRequestManager inFlightRequestManager;

    public ConsumerProxyFactory(ConsumerProperties properties) throws Exception {
        this.properties = properties;
        this.serviceRegistry = new DefaultServiceRegistry(properties.getServiceRegistryConfig());
        this.serviceRegistry.init(properties.getServiceRegistryConfig());
        this.balancer = createLoaderBalancer(properties.getLoadBalancePolicy());
        this.retryPolicy = createRetryPolicy(properties.getRetryPolicy());
        this.inFlightRequestManager = new InFlightRequestManager(properties);
        this.connectionManager = new ConnectionManager(properties, inFlightRequestManager);
    }

    /**
     * 关闭代理工厂，释放资源
     */
    public void shutdown() {
        log.info("关闭 ConsumerProxyFactory，正在清理资源...");
        connectionManager.shutdown();
        inFlightRequestManager.shutdown();
        log.info("ConsumerProxyFactory 已关闭");
    }

    @SuppressWarnings("unchecked")
    public <I> I createConsumerProxy(Class<I> interfaceClass) {
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[]{interfaceClass}
                , new ConsumerInvocationHandler(interfaceClass, balancer));
    }

    private LoaderBalancer createLoaderBalancer(String loadBalancePolicy) {
        switch (loadBalancePolicy) {
            case "random" -> {
                return new RandomLoaderBalancer();
            }
            case "roundRobin" -> {
                return new RoundRobinLoaderBalancer();
            }
            default -> throw new IllegalArgumentException("Unsupported load balance policy: " + loadBalancePolicy);
        }
    }

    private RetryPolicy createRetryPolicy(String retryPolicy) {
        switch (retryPolicy) {
            case "retrySame" -> {
                return new RetrySamePolicy();
            }
            case "failOver" -> {
                return new FailOverPolicy();
            }
            case "forkAll" -> {
                return new ForkAllPolicy();
            }
            default -> throw new IllegalArgumentException("Unsupported retry policy: " + retryPolicy);
        }
    }


    private class ConsumerInvocationHandler implements InvocationHandler {

        private final Class<?> interfaceClass;

        private final LoaderBalancer balancer;

        private ConsumerInvocationHandler(Class<?> interfaceClass, LoaderBalancer balancer) {
            this.interfaceClass = interfaceClass;
            this.balancer = balancer;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 处理 Object 方法（toString, hashCode, equals）
            if (proxy.getClass().getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }

            // 执行 RPC 调用
            return invokeRemote(method, args);
        }

        /**
         * 处理 Object 类的方法
         */
        private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> "ConsumerProxy for " + interfaceClass.getName();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        /**
         * 执行远程 RPC 调用
         */
        private Object invokeRemote(Method method, Object[] args) throws Exception {

            long startTime = System.currentTimeMillis();
            // 从注册中心获取服务地址
            ServiceMateData service = getServiceFromRegistry();

            // 构建并发送请求
            Request request = buildRequest(method, args);
            try {
                CompletableFuture<Response> future = callRpcAsync(request, service);
                // 等待响应并返回结果
                return future.get(properties.getWaitResponseTimeoutMs(), TimeUnit.MILLISECONDS).getResult();
            } catch (Exception e) {
                return doRetry(method, args, e, startTime, request, service);
            }
        }

        private Object doRetry(Method method, Object[] args, Exception e, long startTime, Request request, ServiceMateData service) throws Exception {
            // 检查是否应该重试
            if (e instanceof ExecutionException ee) {
                Throwable cause = ee.getCause();
                if (cause instanceof RpcException rpcException && !rpcException.retry()) {
                    // 不应该重试的异常（如 LimiterException），直接抛出底层异常
                    throw rpcException;
                }
            }

            // 检查总超时
            if (System.currentTimeMillis() - startTime >= properties.getTotalTimeoutMs()) {
                TimeoutException timeoutEx = new TimeoutException(
                    "RPC 调用总超时 (" + properties.getTotalTimeoutMs() + "ms)，requestId: " + request.getRequestId()
                );
                timeoutEx.initCause(e);
                throw timeoutEx;
            }
            // 遇到异常情况重试
            log.info("RPC 调用异常，进行重试，request: {}", request);
            RetryContext context = new RetryContext();
            context.setFailedService(service);
            context.setRetryList(serviceRegistry.fetchSeviceList(interfaceClass.getName()));
            context.setLoaderBalancer(balancer);
            context.setWaitResponseTimeoutMillis(properties.getWaitResponseTimeoutMs());
            context.setTotalTimeoutMs(properties.getTotalTimeoutMs() - (System.currentTimeMillis() - startTime));
            context.setRetryFunction(retryService -> callRpcAsync(buildRequest(method, args), retryService));
            return retryPolicy.retry(context).getResult();
        }

        /**
         * 异步执行RPC调用
         */
        private CompletableFuture<Response> callRpcAsync(Request request, ServiceMateData service) {
            // 添加请求到等待列表
            CompletableFuture<Response> future =
                    inFlightRequestManager.putRequest(request, service, properties.getWaitResponseTimeoutMs());

            // 获取连接通道
            Channel channel = connectionManager.getChannel(service);
            if (channel == null) {
                future.completeExceptionally(new RpcException("无法连接到服务端，host: " + service.getHost() + ", port: " + service.getPort()));
                return future;
            }

            // 发送请求
            channel.writeAndFlush(request).addListener(f -> {
                if (!f.isSuccess()) {
                    future.completeExceptionally(f.cause());
                }
            });

            return future;
        }

        /**
         * 从注册中心获取服务信息
         */
        private ServiceMateData getServiceFromRegistry() throws Exception {
            List<ServiceMateData> serviceMateDataList = serviceRegistry.fetchSeviceList(interfaceClass.getName());
            log.info("从注册中心获取到服务列表: {}", serviceMateDataList);
            if (serviceMateDataList == null || serviceMateDataList.isEmpty()) {
                throw new RpcException("未找到服务 " + interfaceClass.getName() + " 的注册信息");
            }
            return balancer.select(serviceMateDataList);
        }

        /**
         * 构建 RPC 请求
         */
        private Request buildRequest(Method method, Object[] args) {
            Request request = new Request();
            request.setServiceName(interfaceClass.getName());
            request.setMethodName(method.getName());
            request.setParamsClass(method.getParameterTypes());
            request.setParams(args);
            return request;
        }
    }
}

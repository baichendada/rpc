package com.baichen.rpc.consumer;

import com.baichen.rpc.codec.MessageDecoder;
import com.baichen.rpc.codec.RequestEncoder;
import com.baichen.rpc.exception.RpcException;
import com.baichen.rpc.message.Request;
import com.baichen.rpc.message.Response;
import com.baichen.rpc.registry.DefaultServiceRegistry;
import com.baichen.rpc.registry.ServiceMateData;
import com.baichen.rpc.registry.ServiceRegistry;
import com.baichen.rpc.registry.ServiceRegistryConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * ConsumerProxyFactory 负责创建 RPC 客户端的动态代理对象
 * 通过 Java 的动态代理机制，可以避免接口类中大量的重复代码，简化 RPC 调用的实现
 */
@Slf4j
public class ConsumerProxyFactory {

    // 用于维护正在等待响应的请求，key为请求ID，value为对应的 CompletableFuture
    private static final Map<Integer, CompletableFuture<Response>> IN_FLIGHT_REQUEST_MAP = new ConcurrentHashMap<>();

    private ConnectionManager connectionManager;

    private final ServiceRegistry serviceRegistry;

    private final ConsumerProperties properties;

    public ConsumerProxyFactory(ConsumerProperties properties) throws Exception {
        this.properties = properties;
        this.connectionManager = new ConnectionManager(createBootStrap());
        this.serviceRegistry = new DefaultServiceRegistry(properties.getServiceRegistryConfig());
        this.serviceRegistry.init(properties.getServiceRegistryConfig());
    }

    private Bootstrap createBootStrap() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup(properties.getWorkerThreadNum()))
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMillis())
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel channel) throws Exception {
                        channel.pipeline()
                                // 1. 解码器：解码响应消息
                                .addLast(new MessageDecoder())
                                // 2. 编码器：编码请求消息
                                .addLast(new RequestEncoder())
                                // 3. 业务处理器：处理服务端响应
                                .addLast(new SimpleChannelInboundHandler<Response>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Response resp) throws Exception {
                                        CompletableFuture<Response> resultFuture = IN_FLIGHT_REQUEST_MAP.remove(resp.getRequestId());
                                        if (resultFuture == null) {
                                            log.warn("未找到对应的请求，requestId: {}", resp.getRequestId());
                                            return;
                                        }
                                        // 打印收到的响应
                                        log.info("收到响应: {}", resp);
                                        resultFuture.complete(resp);
                                    }
                                })
                                // 4. 调试用：捕获未处理的消息
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                        log.warn("未处理的消息: {}", msg.getClass().getName());
                                        super.channelRead(ctx, msg);
                                    }
                                });
                    }
                });
        return bootstrap;
    }

    @SuppressWarnings("unchecked")
    public <I> I createConsumerProxy(Class<I> interfaceClass) {
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[]{interfaceClass}
                , new ConsumerInvocationHandler(interfaceClass));
    }

    private class ConsumerInvocationHandler implements InvocationHandler {

        private final Class<?> interfaceClass;

        private ConsumerInvocationHandler(Class<?> interfaceClass) {
            this.interfaceClass = interfaceClass;
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
            CompletableFuture<Response> future = new CompletableFuture<>();

            // 1. 从注册中心获取服务地址
            ServiceMateData service = getServiceFromRegistry();

            // 2. 获取连接通道
            Channel channel = getChannel(service);

            // 3. 构建并发送请求
            Request request = buildRequest(method, args);
            sendRequest(channel, request, future);

            // 4. 等待响应并返回结果
            return waitResponse(future);
        }

        /**
         * 从注册中心获取服务信息
         */
        private ServiceMateData getServiceFromRegistry() throws Exception {
            List<ServiceMateData> serviceMateDataList = serviceRegistry.fetchSeviceList(interfaceClass.getName());
            if (serviceMateDataList == null || serviceMateDataList.isEmpty()) {
                throw new RpcException("未找到服务 " + interfaceClass.getName() + " 的注册信息");
            }
            // todo: 后续可改为负载均衡选择服务
            return serviceMateDataList.get(0);
        }

        /**
         * 获取与服务端的通道连接
         */
        private Channel getChannel(ServiceMateData service) {
            Channel channel = connectionManager.getChannel(service.getHost(), service.getPort());
            if (channel == null) {
                throw new RpcException("无法连接到服务端，host: " + service.getHost() + ", port: " + service.getPort());
            }
            return channel;
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

        /**
         * 发送 RPC 请求
         */
        private void sendRequest(Channel channel, Request request, CompletableFuture<Response> future) throws Exception {
            IN_FLIGHT_REQUEST_MAP.putIfAbsent(request.getRequestId(), future);

            channel.writeAndFlush(request).addListener(f -> {
                if (!f.isSuccess()) {
                    IN_FLIGHT_REQUEST_MAP.remove(request.getRequestId());
                    future.completeExceptionally(f.cause());
                }
            });
        }

        /**
         * 等待并处理响应
         */
        private Object waitResponse(CompletableFuture<Response> future) throws Exception {
            Response resp = future.get(properties.getWaitResponseTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (Response.ResponseCode.SUCCESS.getCode() == resp.getCode()) {
                return resp.getResult();
            }
            throw new RpcException("RPC 调用失败，错误信息: " + resp);
        }
    }
}

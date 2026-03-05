package com.baichen.rpc.consumer;

import com.baichen.rpc.api.Add;
import com.baichen.rpc.codec.MessageDecoder;
import com.baichen.rpc.codec.RequestEncoder;
import com.baichen.rpc.exception.RpcException;
import com.baichen.rpc.message.Request;
import com.baichen.rpc.message.Response;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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

    private final ConnectionManager connectionManager = new ConnectionManager(createBootStrap());

    private Bootstrap createBootStrap() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup(4))
                .channel(NioSocketChannel.class)
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
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[]{interfaceClass}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if (proxy.getClass().getDeclaringClass() == Object.class) {
                    if (method.getName().equals("toString")) {
                        return "ConsumerProxy for " + interfaceClass.getName();
                    } else if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    } else if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                }

                try {
                    // 用于接收服务端响应的 CompletableFuture
                    CompletableFuture<Response> future = new CompletableFuture<>();

                    // 连接到服务端
                    Channel channel = connectionManager.getChannel("localhost", 8085);
                    if (channel == null) {
                        throw new RpcException("无法连接到服务端，host: localhost, port: 8085");
                    }

                    // 构建 RPC 请求
                    Request request = new Request();
                    request.setServiceName(interfaceClass.getName());
                    request.setMethodName(method.getName());
                    request.setParamsClass(method.getParameterTypes());
                    request.setParams(args);

                    // 发送请求
                    channel.writeAndFlush(request).addListener(f -> {
                                if (f.isSuccess()) {
                                    IN_FLIGHT_REQUEST_MAP.putIfAbsent(request.getRequestId(), future);
                                }
                            }
                    );

                    Response resp = future.get(3, TimeUnit.SECONDS);
                    if (Response.ResponseCode.SUCCESS.getCode() == resp.getCode()) {
                        return resp.getResult();
                    }
                    throw new RpcException("RPC 调用失败，错误信息: " + resp);
                } catch (RpcException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}

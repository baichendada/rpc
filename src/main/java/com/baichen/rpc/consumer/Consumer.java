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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * RPC 客户端
 * <p>
 * 负责向服务端发起远程调用
 * <p>
 * Pipeline 顺序:
 * 1. MessageDecoder - 解码响应消息
 * 2. RequestEncoder - 编码请求消息
 * 3. SimpleChannelInboundHandler - 处理响应
 * 4. ChannelInboundHandlerAdapter - 捕获未处理的消息（用于调试）
 */
@Slf4j
public class Consumer implements Add {
    // 用于维护正在等待响应的请求，key为请求ID，value为对应的 CompletableFuture
    private static final Map<Integer, CompletableFuture<Integer>> IN_FLIGHT_REQUEST_MAP = new ConcurrentHashMap<>();

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
                                        CompletableFuture<Integer> resultFuture = IN_FLIGHT_REQUEST_MAP.remove(resp.getRequestId());
                                        // 打印收到的响应
                                        log.info("收到响应: {}", resp);
                                        // 从响应中获取结果并完成 Future
                                        if (Response.ResponseCode.SUCCESS.getCode() == resp.getCode()) {
                                            Integer result = Integer.parseInt(resp.getResult().toString());
                                            resultFuture.complete(result);
                                        } else {
                                            resultFuture.completeExceptionally(new RpcException(resp.getErrorMessage()));
                                        }
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

    /**
     * 发起 RPC 调用
     *
     * @param a 加数
     * @param b 被加数
     * @return 计算结果
     */
    @Override
    public int add(int a, int b) {
        try {
            // 用于接收服务端响应的 CompletableFuture
            CompletableFuture<Integer> future = new CompletableFuture<>();

            // 连接到服务端
            Channel channel = connectionManager.getChannel("localhost", 8085);
            if (channel == null) {
                throw new RpcException("无法连接到服务端，host: localhost, port: 8085");
            }

            // 构建 RPC 请求
            Request request = new Request();
            request.setServiceName(Add.class.getName());
            request.setMethodName("add");
            request.setParamsClass(new Class<?>[]{int.class, int.class});
            request.setParams(new Object[]{a, b});

            // 发送请求
            channel.writeAndFlush(request).addListener(f -> {
                    if (f.isSuccess()) {
                        IN_FLIGHT_REQUEST_MAP.putIfAbsent(request.getRequestId(), future);
                    }
                }
            );

            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

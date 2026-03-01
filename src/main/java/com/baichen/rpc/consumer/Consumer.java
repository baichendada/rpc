package com.baichen.rpc.consumer;

import com.baichen.rpc.api.Add;
import com.baichen.rpc.codec.MessageDecoder;
import com.baichen.rpc.codec.RequestEncoder;
import com.baichen.rpc.message.Request;
import com.baichen.rpc.message.Response;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.CompletableFuture;

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
public class Consumer implements Add {

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

            // 创建 Netty Bootstrap
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
                                            // 打印收到的响应
                                            System.out.println("收到响应: " + resp);
                                            // 从响应中获取结果并完成 Future
                                            Integer result = Integer.parseInt(resp.getResult().toString());
                                            future.complete(result);
                                        }
                                    })
                                    // 4. 调试用：捕获未处理的消息
                                    .addLast(new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                            System.out.println("未处理的消息: " + msg.getClass().getName());
                                            super.channelRead(ctx, msg);
                                        }
                                    });
                        }
                    });

            // 连接到服务端
            ChannelFuture channelFuture = bootstrap.connect("localhost", 8085).sync();

            // 构建 RPC 请求
            Request request = new Request();
            request.setServiceName(Add.class.getName());
            request.setMethodName("add");
            request.setParamsClass(new Class<?>[]{int.class, int.class});
            request.setParams(new Object[]{a, b});

            // 发送请求
            channelFuture.channel().writeAndFlush(request);

            // 阻塞等待响应返回
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

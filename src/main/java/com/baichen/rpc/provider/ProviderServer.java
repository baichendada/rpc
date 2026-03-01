package com.baichen.rpc.provider;

import com.baichen.rpc.codec.MessageDecoder;
import com.baichen.rpc.codec.ResponseEncoder;
import com.baichen.rpc.message.Request;
import com.baichen.rpc.message.Response;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * RPC 服务端
 * <p>
 * 负责接收客户端请求，处理并返回结果
 * <p>
 * Pipeline 顺序:
 * 1. MessageDecoder - 解码收到的请求
 * 2. ResponseEncoder - 编码响应消息
 * 3. SimpleChannelInboundHandler - 业务处理
 */
public class ProviderServer {

    /**
     * 服务端口
     */
    private final int port;

    private final ProviderRegister providerRegister;

    /**
     * Boss 事件循环组，负责处理 Accept 事件
     */
    private EventLoopGroup bossEventGroup;

    /**
     * Worker 事件循环组，负责处理 Read/Write 事件
     */
    private EventLoopGroup workerEventGroup;

    ProviderServer(int port) {
        this.port = port;
        this.providerRegister = new ProviderRegister();
    }

    /**
     * 启动 RPC 服务端
     */
    public void start() {
        try {
            ServerBootstrap bs = new ServerBootstrap();

            // 初始化事件循环组
            // bossEventGroup: 1 个线程处理连接Accept
            // workerEventGroup: 4 个线程处理 IO 读写
            bossEventGroup = new NioEventLoopGroup();
            workerEventGroup = new NioEventLoopGroup(4);

            bs.group(bossEventGroup, workerEventGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel channel) throws Exception {
                            channel.pipeline()
                                    // 1. 解码器：解码请求消息
                                    .addLast(new MessageDecoder())
                                    // 2. 编码器：编码响应消息
                                    .addLast(new ResponseEncoder())
                                    // 3. 业务处理器：处理请求并返回响应
                                    .addLast(new SimpleChannelInboundHandler<Request>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, Request req) throws Exception {
                                            // 打印收到的请求
                                            System.out.println("收到请求: " + req);

                                            // 根据入参，从注册表中查找对应的服务实例并调用
                                            ProviderRegister.InvokerInstance<?> invokerInstance = providerRegister.findInvokerInstance(req.getServiceName());
                                            Object result = invokerInstance.invoke(req.getMethodName(), req.getParamsClass(), req.getParams());

                                            // 创建响应对象
                                            Response response = new Response();
                                            // 计算结果
                                            response.setResult(result);

                                            // 发送响应给客户端
                                            ctx.channel().writeAndFlush(response);
                                        }

                                        @Override
                                        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                                            super.channelRegistered(ctx);
                                            System.out.println("客户端连接成功");
                                        }
                                    });
                        }
                    });

            // 绑定端口并启动服务
            ChannelFuture channelFuture = bs.bind(port).sync();
            System.out.println("RPC 服务端启动成功，监听端口: " + port);

            // 阻塞等待服务端关闭
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            System.out.println("服务端启动失败");
            throw new RuntimeException(e);
        }
    }

    public <I> void register(Class<I> iClass, I instance) {
        providerRegister.register(iClass, instance);
    }

    /**
     * 停止 RPC 服务端
     */
    public void stop() {
        if (bossEventGroup != null) {
            bossEventGroup.shutdownGracefully();
        }
        if (workerEventGroup != null) {
            workerEventGroup.shutdownGracefully();
        }
    }

    /**
     * 模拟的加法服务
     * 后续版本会通过反射动态调用
     */
    static int add(int a, int b) {
        return a + b;
    }
}

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
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
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
                                    .addLast(new ProviderServerHandler());
                        }
                    });

            // 绑定端口并启动服务
            ChannelFuture channelFuture = bs.bind(port).sync();
            log.info("RPC 服务端启动成功，监听端口: {}", port);

            // 阻塞等待服务端关闭
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("服务端启动失败", e);
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

    private class ProviderServerHandler extends SimpleChannelInboundHandler<Request> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Request req) throws Exception {
            // 打印收到的请求
            log.info("收到请求: {}", req);

            // 根据入参，从注册表中查找对应的服务实例并调用
            ProviderRegister.InvokerInstance<?> invokerInstance = providerRegister.findInvokerInstance(req.getServiceName());
            if (invokerInstance == null) {
                log.error("未找到服务实例: {}", req.getServiceName());
                Response response = Response.fail("服务未找到: " + req.getServiceName(), req.getRequestId());;
                ctx.channel().writeAndFlush(response);
                return;
            }
            try {
                Object result = invokerInstance.invoke(req.getMethodName(), req.getParamsClass(), req.getParams());
                Response response = Response.success(result, req.getRequestId());
                ctx.channel().writeAndFlush(response);
            } catch (Exception e) {
                log.error("调用服务实例失败: {}", e.getMessage());
                Response response = new Response();
                ctx.channel().writeAndFlush(response);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("channel active: {}", ctx.channel().remoteAddress());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("channel inactive: {}", ctx.channel().remoteAddress());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("channel exception: {}", cause.getMessage());
            ctx.close();
        }
    }
}

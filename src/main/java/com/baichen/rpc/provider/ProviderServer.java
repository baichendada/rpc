package com.baichen.rpc.provider;

import com.baichen.rpc.codec.MessageDecoder;
import com.baichen.rpc.codec.ResponseEncoder;
import com.baichen.rpc.limiter.ConcurrencyLimiter;
import com.baichen.rpc.limiter.Limiter;
import com.baichen.rpc.limiter.RateLimiter;
import com.baichen.rpc.message.Request;
import com.baichen.rpc.message.Response;
import com.baichen.rpc.registry.DefaultServiceRegistry;
import com.baichen.rpc.registry.ServiceMateData;
import com.baichen.rpc.registry.ServiceRegistry;
import com.baichen.rpc.registry.ServiceRegistryConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

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

    private final ProviderProperties properties;

    /**
     * 服务主机地址
     */
    private final String host;

    /**
     * 服务端口
     */
    private final int port;

    private final ProviderRegistry providerRegistry;

    private final ServiceRegistry serviceRegistry;

    private final Limiter globalLimiter;

    /**
     * 全局服务速率限流器（所有连接共享）
     */
    private final Limiter serviceLimiter;

    /**
     * Boss 事件循环组，负责处理 Accept 事件
     */
    private EventLoopGroup bossEventGroup;

    /**
     * Worker 事件循环组，负责处理 Read/Write 事件
     */
    private EventLoopGroup workerEventGroup;

    ProviderServer(ProviderProperties properties) throws Exception {
        this.host = properties.getHost();
        this.port = properties.getPort();
        this.providerRegistry = new ProviderRegistry();
        this.serviceRegistry = new DefaultServiceRegistry(properties.getServiceRegistryConfig());
        this.serviceRegistry.init(properties.getServiceRegistryConfig());
        this.properties = properties;
        this.globalLimiter = new ConcurrencyLimiter(properties.getGlobalLimit());
        this.serviceLimiter = new RateLimiter(properties.getServiceLimit());
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
            workerEventGroup = new NioEventLoopGroup(properties.getWorkerThreadNum());

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
                                    // 3. 限流器：全局和接口限流
                                    .addLast(new LimiterServerHandler())
                                    // 4. 业务处理器：处理请求并返回响应
                                    .addLast(new ProviderServerHandler());
                        }
                    });

            // 绑定端口并启动服务
            ChannelFuture channelFuture = bs.bind(port).sync();
            log.info("RPC 服务端启动成功，监听端口: {}", port);

            // 将注册的接口信息注册到服务注册中心
            providerRegistry.getAllServiceNames().stream()
                    .map(name -> new ServiceMateData(name, host, port))
                    .forEach(data -> {
                        try {
                            serviceRegistry.registry(data);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });

            // 阻塞等待服务端关闭
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("服务端启动失败", e);
            throw new RuntimeException(e);
        }
    }

    public <I> void register(Class<I> iClass, I instance) {
        providerRegistry.register(iClass, instance);
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
     * 限流处理器
     * <p>
     * 实现两级限流：
     * <ul>
     *   <li>全局并发限流：限制服务端同时处理的最大请求数（ConcurrencyLimiter）</li>
     *   <li>全局速率限流：限制服务端每秒处理的请求数（RateLimiter，所有连接共享）</li>
     * </ul>
     * </p>
     * <p>
     * 使用 AttributeKey 跟踪每个请求 ID，确保每个请求的许可只释放一次。
     * </p>
     */
    private class LimiterServerHandler extends ChannelDuplexHandler {

        /**
         * 跟踪当前 Channel 上已获取全局许可的请求 ID
         */
        AttributeKey<java.util.Set<Integer>> ATTR_KEY_REQUEST_IDS =
            AttributeKey.valueOf("attr_key_request_ids");

        /**
         * 处理入站请求，进行两级限流检查
         * <p>
         * 限流策略：
         * <ol>
         *   <li><b>全局并发限流</b>：限制服务端同时处理的最大请求数（ConcurrencyLimiter）</li>
         *   <li><b>全局速率限流</b>：限制服务端每秒处理的请求数（RateLimiter，所有连接共享）</li>
         * </ol>
         * </p>
         * <p>
         * 如果通过限流检查，将请求 ID 添加到跟踪集合，用于在 write 完成后释放许可。
         * </p>
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Request request = (Request) msg;

            // ========== 第一级：全局并发限流 ==========
            // 限制服务端同时处理的最大请求数，防止服务端资源耗尽
            if (!globalLimiter.tryAcquire()) {
                log.warn("全局并发限流触发，拒绝请求: {}", request.getRequestId());
                Response response = Response.fail("服务繁忙，请稍后重试（全局并发限制）", request.getRequestId());
                ctx.writeAndFlush(response);
                return;
            }

            // ========== 第二级：全局速率限流 ==========
            // 所有连接共享同一个 RateLimiter，限制服务端的总体 QPS
            if (!serviceLimiter.tryAcquire()) {
                log.warn("全局速率限流触发，拒绝请求: {}", request.getRequestId());
                globalLimiter.release();  // 释放已获取的全局并发许可（避免资源泄漏）
                Response response = Response.fail("服务繁忙，请稍后重试（速率限制）", request.getRequestId());
                ctx.writeAndFlush(response);
                return;
            }

            // ========== 跟踪请求 ID ==========
            // 将请求 ID 添加到 Channel 的跟踪集合中
            // 在 write 完成后，通过此集合判断是否需要释放许可
            java.util.Set<Integer> requestIds = ctx.channel().attr(ATTR_KEY_REQUEST_IDS).get();
            if (requestIds != null) {
                requestIds.add(request.getRequestId());
            } else {
                log.error("REQUEST_IDS 集合未初始化，channel: {}", ctx.channel());
            }

            // 传递给下一个 Handler 处理
            ctx.fireChannelRead(msg);
        }

        /**
         * 拦截出站响应，在 write 完成后释放许可
         * <p>
         * 通过 promise.addListener 监听 write 完成事件：
         * <ul>
         *   <li>从跟踪集合中移除请求 ID</li>
         *   <li>如果移除成功，说明此请求确实获取了许可，需要释放</li>
         *   <li>如果移除失败（返回 false），说明请求未被跟踪或已释放，不做处理</li>
         * </ul>
         * </p>
         * <p>
         * 这种机制确保：
         * <ul>
         *   <li>每个请求的许可只释放一次（通过 Set.remove 的原子性保证）</li>
         *   <li>限流拒绝的请求不会释放许可（因为未添加到跟踪集合）</li>
         * </ul>
         * </p>
         */
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof Response response) {
                promise.addListener(future -> {
                    java.util.Set<Integer> requestIds = ctx.channel().attr(ATTR_KEY_REQUEST_IDS).get();
                    if (requestIds != null && requestIds.remove(response.getRequestId())) {
                        // 只有当此请求 ID 被成功移除时才释放许可
                        // remove() 返回 true 说明此 ID 确实在集合中，即获取了许可
                        globalLimiter.release();
                        serviceLimiter.release();  // RateLimiter.release() 是 no-op，但为了对称性仍然调用
                    }
                });
            }
            ctx.write(msg, promise);
        }

        /**
         * 连接建立时初始化请求 ID 跟踪集合
         * <p>
         * 使用 ConcurrentHashMap.newKeySet() 创建线程安全的 Set，
         * 用于跟踪当前 Channel 上所有已获取许可的请求 ID。
         * </p>
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // 初始化请求 ID 跟踪集合（使用线程安全的 Set）
            ctx.channel().attr(ATTR_KEY_REQUEST_IDS).set(java.util.concurrent.ConcurrentHashMap.newKeySet());
            ctx.fireChannelActive();
        }

        /**
         * 连接关闭时释放所有未完成请求的许可
         * <p>
         * 如果连接异常关闭，可能有些请求的响应还未发送，
         * 这些请求的许可需要在此释放，避免资源泄漏。
         * </p>
         * <p>
         * 注意：只释放全局并发许可（ConcurrencyLimiter），
         * 不释放速率限流许可（RateLimiter 是时间窗口限流，无需 release）。
         * </p>
         */
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // 连接关闭时，释放所有未完成请求的许可
            java.util.Set<Integer> requestIds = ctx.channel().attr(ATTR_KEY_REQUEST_IDS).get();
            if (requestIds != null && !requestIds.isEmpty()) {
                int count = requestIds.size();
                log.warn("连接关闭时仍有 {} 个请求未完成，释放对应的全局并发许可", count);
                globalLimiter.release(count);
                // serviceLimiter 不需要释放（RateLimiter 是时间窗口限流，不需要 release）
                requestIds.clear();
            }
            ctx.fireChannelInactive();
        }
    }

    private class ProviderServerHandler extends SimpleChannelInboundHandler<Request> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Request req) throws Exception {
            // 打印收到的请求
            log.info("收到请求: {}", req);

            // 根据入参，从注册表中查找对应的服务实例并调用
            ProviderRegistry.InvokerInstance<?> invokerInstance = providerRegistry.findInvokerInstance(req.getServiceName());
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

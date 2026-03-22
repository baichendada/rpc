package com.baichen.rpc.consumer;

import com.baichen.rpc.codec.ChannelAttributes;
import com.baichen.rpc.codec.MessageDecoder;
import com.baichen.rpc.codec.MessageEncoder;
import com.baichen.rpc.compressor.Compressor;
import com.baichen.rpc.compressor.CompressorManager;
import com.baichen.rpc.message.Response;
import com.baichen.rpc.registry.ServiceMateData;
import com.baichen.rpc.serializer.Serializer;
import com.baichen.rpc.serializer.SerializerManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 相同地址的连接可以用连接管理器进行维护，避免重复创建连接浪费资源
 */
@Slf4j
public class ConnectionManager {

    private final Map<String, ChannelWrapper> channelMap = new ConcurrentHashMap<>();

    private final Bootstrap bootstrap;

    private final InFlightRequestManager inFlightRequestManager;

    private final ConsumerProperties properties;

    /**
     * Worker 事件循环组，需要在 shutdown 时关闭
     */
    private final EventLoopGroup workerGroup;

    private final SerializerManager serializerManager;

    private final CompressorManager compressorManager;

    public ConnectionManager(ConsumerProperties properties, InFlightRequestManager inFlightRequestManager) {
        this.properties = properties;
        this.workerGroup = new NioEventLoopGroup(properties.getWorkerThreadNum());
        this.bootstrap = createBootStrap();
        this.inFlightRequestManager = inFlightRequestManager;
        this.serializerManager = new SerializerManager();
        this.compressorManager = new CompressorManager();
    }

    private Bootstrap createBootStrap() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel channel) throws Exception {
                        channel.pipeline()
                                // 1. 解码器：解码响应消息
                                .addLast(new MessageDecoder())
                                // 2. 编码器：编码请求消息
                                .addLast(new MessageEncoder())
                                // 3. 业务处理器：处理服务端响应
                                .addLast(new ConsumerChannelHandler())
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



    public Channel getChannel(ServiceMateData service) {
        String host = service.getHost();
        int port = service.getPort();
        String key = host + ":" + port;

        channelMap.computeIfAbsent(key, k -> {
            try {
                ChannelFuture channelFuture = bootstrap.connect(host, port).sync();
                Channel channel = channelFuture.channel();
                channel.closeFuture().addListener(f -> {
                    // 连接关闭时，从 channelMap 中移除对应的 ChannelWrapper
                    channelMap.remove(key);
                    inFlightRequestManager.clearChannel(service);
                });
                return new ChannelWrapper(channel);
            } catch (InterruptedException e) {
                return new ChannelWrapper(null);
            }
        });

        Channel channel = channelMap.get(key).getChannel();
        // 如果连接不可用，就从 channelMap 中移除对应的 ChannelWrapper，并返回 null
        if (channel == null || !channel.isActive()) {
            channelMap.remove(key);
            return null;
        }
        return channel;
    }

    private class ConsumerChannelHandler extends SimpleChannelInboundHandler<Response> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Response resp) throws Exception {
            inFlightRequestManager.completeRequest(resp.getRequestId(), resp);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("channel active: {}", ctx.channel().remoteAddress());
            ctx.channel().attr(ChannelAttributes.SERIALIZER_KEY).set(Serializer.SerializerType.valueOf(properties.getSerializerType().toUpperCase(Locale.ROOT)).getCode());
            ctx.channel().attr(ChannelAttributes.COMPRESSOR_KEY).set(Compressor.CompressorType.valueOf(properties.getCompressorType().toUpperCase(Locale.ROOT)).getCode());
            ctx.channel().attr(ChannelAttributes.SERIALIZER_MANAGER).set(serializerManager);
            ctx.channel().attr(ChannelAttributes.COMPRESSOR_MANAGER).set(compressorManager);
            ctx.fireChannelActive();
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

    /**
     * 关闭连接管理器，释放资源
     */
    public void shutdown() {
        log.info("关闭 ConnectionManager，正在清理资源...");
        // 关闭所有连接
        channelMap.values().forEach(wrapper -> {
            Channel channel = wrapper.getChannel();
            if (channel != null && channel.isActive()) {
                channel.close();
            }
        });
        channelMap.clear();
        // 关闭 EventLoopGroup
        workerGroup.shutdownGracefully();
        log.info("ConnectionManager 已关闭");
    }

    /**
     * 包装类，用来包装bootstrap.connect连接返回的Channel对象
     * 如果失败，就返回一个包装类对象，里面的Channel为null，这样就可以避免ConcurrentHashMap中出现null值和null键的问题
     */
    @Getter
    private static class ChannelWrapper {
        private final Channel channel;

        public ChannelWrapper(Channel channel) {
            this.channel = channel;
        }
    }
}

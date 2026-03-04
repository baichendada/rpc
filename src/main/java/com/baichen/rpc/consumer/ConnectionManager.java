package com.baichen.rpc.consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 相同地址的连接可以用连接管理器进行维护，避免重复创建连接浪费资源
 */
public class ConnectionManager {

    private final Map<String, ChannelWrapper> channelMap = new ConcurrentHashMap<>();

    private final Bootstrap bootstrap;

    public ConnectionManager(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public Channel getChannel(String host, int port) {
        String key = host + ":" + port;

        channelMap.computeIfAbsent(key, k -> {
            try {
                ChannelFuture channelFuture = bootstrap.connect(host, port).sync();
                Channel channel = channelFuture.channel();
                channel.closeFuture().addListener(f -> {
                    // 连接关闭时，从 channelMap 中移除对应的 ChannelWrapper
                    channelMap.remove(key);
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

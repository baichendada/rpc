package com.baichen.rpc.handler;

import com.baichen.rpc.message.HeartbeatRequest;
import com.baichen.rpc.message.HeartbeatResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HeartbeatHandler extends SimpleChannelInboundHandler<Object> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object o) throws Exception {
        if (o instanceof HeartbeatRequest request) {
            ctx.writeAndFlush(new HeartbeatResponse(request.getRequestTime()));
            // 心跳请求已处理，不再传递给后续 handler
            return;
        } else if (o instanceof HeartbeatResponse response) {
            long latency = System.currentTimeMillis() - response.getRequestTime();
            log.info("{} 心跳响应，延迟 {} ms", ctx.channel().remoteAddress(), latency);
            // 心跳响应已处理，不再传递给后续 handler
            return;
        }

        // 非心跳消息，传递给后续 handler 处理
        ctx.fireChannelRead(o);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleStateEvent) {
            switch (idleStateEvent.state()) {
                case READER_IDLE -> {
                    log.info("{} 读空闲事件触发，关闭连接", ctx.channel().remoteAddress());
                    ctx.channel().close();
                }
                case WRITER_IDLE -> {
                    log.info("{} 写空闲事件触发", ctx.channel().remoteAddress());
                    ctx.writeAndFlush(new HeartbeatRequest());
                }
            }
        }
        ctx.fireUserEventTriggered(evt);
    }
}

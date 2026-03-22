package com.baichen.rpc.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class TrafficRecordHandler extends ChannelDuplexHandler {

    private TrafficRecord trafficRecord;

    private ScheduledFuture<?> trafficRecordFuture;

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf byteBuf) {
            trafficRecord.getUpCounter().getAndAdd(byteBuf.readableBytes());
        }
        ctx.write(msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf byteBuf) {
            trafficRecord.getDownCounter().getAndAdd(byteBuf.readableBytes());
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        trafficRecord = new TrafficRecord();
        trafficRecordFuture = ctx.channel().eventLoop().scheduleAtFixedRate(() -> {
            log.info("上行流量：{} bytes，下行流量：{} bytes", trafficRecord.getUpCounter().get(), trafficRecord.getDownCounter().get());
        }, 5, 5, TimeUnit.SECONDS);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (trafficRecordFuture != null) {
            trafficRecordFuture.cancel(false);
        }
        ctx.fireChannelInactive();
    }



    @Data
    public static class TrafficRecord {
        private final AtomicLong upCounter = new AtomicLong();
        private final AtomicLong downCounter = new AtomicLong();
    }
}

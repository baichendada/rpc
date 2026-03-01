package com.baichen.rpc;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class Provider {

    public static void main(String[] args) throws Exception {
        ServerBootstrap bs = new ServerBootstrap();
        bs.group(new NioEventLoopGroup(), new NioEventLoopGroup(4))
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel channel) throws Exception {
                        channel.pipeline()
                                .addLast(new LineBasedFrameDecoder(1024))
                                .addLast(new StringDecoder())
                                .addLast(new StringEncoder())
                                .addLast(new SimpleChannelInboundHandler<String>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, String s) throws Exception {
                                        System.out.println(s);
                                        String[] split = s.split(",");
                                        String method = split[0];
                                        int a = Integer.parseInt(split[1]);
                                        int b = Integer.parseInt(split[2]);
                                        if (method.equals("add")) {
                                            int result = add(a, b);
                                            ctx.channel().writeAndFlush(result + "\n");
                                        }
                                    }

                                    @Override
                                    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                                        super.channelRegistered(ctx);
                                        System.out.println("channelRegistered");
                                    }
                                });
                    }
                });
        ChannelFuture channelFuture = bs.bind(8085).sync();
    }

    static int add(int a, int b) {
        return a + b;
    }
}

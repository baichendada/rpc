package com.baichen.rpc.codec;

import com.baichen.rpc.compressor.Compressor;
import com.baichen.rpc.compressor.CompressorManager;
import com.baichen.rpc.message.Message;
import com.baichen.rpc.message.Version;
import com.baichen.rpc.serializer.Serializer;
import com.baichen.rpc.serializer.SerializerManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class MessageEncoder extends MessageToByteEncoder<Object> {

    private volatile byte defaultSACType;
    private volatile Serializer defaultSerializer;
    private volatile Compressor defaultCompressor;

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        initIfNecessary(ctx);
        // 0. 获取消息类型
        Message.MessageType messageType = Message.MessageType.getByType(msg.getClass());
        if (messageType == null) {
            throw new IllegalArgumentException("不支持的类型");
        }
        // 1. 获取魔数和消息类型
        byte[] magic = Message.MAGIC;
        byte messageTypeCode = messageType.getCode();
        short version = Version.V1.getCode();
        byte serializeTypeCode = defaultSACType;

        // 2. 序列化消息体；小消息不压缩，将压缩码替换为 NONE
        byte[] body = defaultSerializer.serialize(msg);
        if (body.length <= 256) {
            serializeTypeCode &= (byte) 0xF0;
        } else {
            body = defaultCompressor.compress(body);
        }

        // 3. 计算总长度（不包括 Length 字段自己）
        int length = magic.length + 2* Byte.BYTES + Short.BYTES + body.length;

        // 4. 按协议格式写入 ByteBuf
        out.writeInt(length);
        out.writeBytes(magic);
        out.writeByte(messageTypeCode);
        out.writeShort(version);
        out.writeByte(serializeTypeCode);
        out.writeBytes(body);
    }

    private void initIfNecessary(ChannelHandlerContext ctx) {
        if (defaultSerializer != null && defaultCompressor != null) {
            return;
        }
        SerializerManager serializerManager = ctx.channel().attr(ChannelAttributes.SERIALIZER_MANAGER).get();
        String serializerStr = ctx.channel().attr(ChannelAttributes.SERIALIZER_KEY).get();
        defaultSerializer = serializerManager.getSerializer(serializerStr);

        CompressorManager compressorManager = ctx.channel().attr(ChannelAttributes.COMPRESSOR_MANAGER).get();
        String compressorStr = ctx.channel().attr(ChannelAttributes.COMPRESSOR_KEY).get();
        defaultCompressor = compressorManager.getCompressor(compressorStr);

        if (defaultSerializer == null) {
            throw new IllegalArgumentException("缺少序列化器");
        }
        if (defaultCompressor == null) {
            throw new IllegalArgumentException("缺少压缩器");
        }

        defaultSACType = (byte) (defaultSerializer.getCode() << 4 | defaultCompressor.getCode());
    }
}

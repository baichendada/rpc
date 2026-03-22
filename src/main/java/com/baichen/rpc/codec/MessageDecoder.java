package com.baichen.rpc.codec;

import com.baichen.rpc.compressor.Compressor;
import com.baichen.rpc.compressor.CompressorManager;
import com.baichen.rpc.message.Message;
import com.baichen.rpc.message.Version;
import com.baichen.rpc.serializer.Serializer;
import com.baichen.rpc.serializer.SerializerManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.util.Arrays;

/**
 * 消息解码器
 * <p>
 * 协议格式:
 * +----------+--------+----------+---------+---------+--------+
 * |  Length  |  Magic |   Type   | Version | SACType |  Body  |
 * +----------+--------+----------+---------+---------+--------+
 * |   4B     |   6B   |   1B     |   2B    |   1B    |  N B   |
 * +----------+--------+----------+---------+---------+--------+
 *
 * Length:  整个消息的长度（不包括 Length 字段自己）
 * Magic:   魔数 "baichen"，用于协议校验
 * Type:    消息类型 (1=Request, 2=Response)
 * Version: 协议版本 (1=V1)
 * SACType: 上四位=序列化类型，下四位=压缩类型
 * Body:    消息体，按序列化类型编码
 */
public class MessageDecoder extends LengthFieldBasedFrameDecoder {

    private static final int MAX_FRAME_LENGTH = 1024 * 1024;

    private volatile SerializerManager serializerManager;
    private volatile CompressorManager compressorManager;

    public MessageDecoder() {
        // LengthFieldBasedFrameDecoder 参数说明:
        // maxFrameLength: 最大帧长度
        // lengthFieldOffset: 长度字段偏移量
        // lengthFieldLength: 长度字段长度（4字节int）
        // lengthAdjustment: 长度调整值（Length字段后还需要跳过的字节数）
        // initialBytesToStrip: 长度字段之后需要跳过的字节数（通常为长度字段本身的长度）
        super(MAX_FRAME_LENGTH, 0, Integer.BYTES, 0, Integer.BYTES);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        initIfNecessary(ctx);
        // 调用父类进行粘包处理，获取完整的一帧数据
        ByteBuf decode = (ByteBuf) super.decode(ctx, in);
        if (decode == null) {
            return null;
        }

        try {
            // 1. 读取并校验魔数
            byte[] bytes = new byte[Message.MAGIC.length];
            decode.readBytes(bytes);
            if (!Arrays.equals(bytes, Message.MAGIC)) {
                throw new IllegalArgumentException("魔数验证错误");
            }

            // 2. 读取消息类型
            byte messageTypeCode = decode.readByte();
            Message.MessageType messageType = Message.MessageType.getByCode(messageTypeCode);
            if (messageType == null) {
                throw new IllegalArgumentException("不支持的消息类型");
            }
            short version = decode.readShort();
            if (version != Version.V1.getCode()) {
                throw new IllegalArgumentException("版本不匹配");
            }
            byte SACType = decode.readByte();
            byte serializerCode = (byte) (SACType >>> 4);
            byte compressorCode = (byte) (SACType & 0b00001111);
            Serializer serializer = serializerManager.getSerializerByCode(serializerCode);
            Compressor compressor = compressorManager.getCompressorByCode(compressorCode);
            if (serializer == null || compressor == null) {
                throw new IllegalArgumentException("缺少序列化器或压缩器");
            }

            // 3. 读取消息体并反序列化
            byte[] body = new byte[decode.readableBytes()];
            decode.readBytes(body);
            byte[] decompress = compressor.decompress(body);
            return serializer.deserialize(decompress, messageType.getType());
        } finally {
            decode.release();
        }
    }

    private void initIfNecessary(ChannelHandlerContext ctx) {
        if (serializerManager != null && compressorManager != null) {
            return;
        }
        serializerManager = ctx.channel().attr(ChannelAttributes.SERIALIZER_MANAGER).get();
        compressorManager = ctx.channel().attr(ChannelAttributes.COMPRESSOR_MANAGER).get();

        if (serializerManager == null) {
            throw new IllegalArgumentException("缺少序列化器");
        }
        if (compressorManager == null) {
            throw new IllegalArgumentException("缺少压缩器");
        }
    }
}

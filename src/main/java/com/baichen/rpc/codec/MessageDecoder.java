package com.baichen.rpc.codec;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.baichen.rpc.message.Request;
import com.baichen.rpc.message.Response;
import com.baichen.rpc.message.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 消息解码器
 * <p>
 * 协议格式:
 * +----------+--------+----------+
 * |  Length  |  Magic |   Type   |  Body
 * +----------+--------+----------+
 * |   4B    |   6B   |   1B     |  N B
 * +----------+--------+----------+
 *
 * Length: 整个消息的长度（不包括Length自己）
 * Magic:  魔数 "baichen"，用于协议校验
 * Type:   消息类型 (1=Request, 2=Response)
 * Body:   消息体，JSON格式
 */
public class MessageDecoder extends LengthFieldBasedFrameDecoder {

    private static final int MAX_FRAME_LENGTH = 1024 * 1024;

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
        // 调用父类进行粘包处理，获取完整的一帧数据
        ByteBuf decode = (ByteBuf) super.decode(ctx, in);
        if (decode == null) {
            return null;
        }

        // 1. 读取并校验魔数
        byte[] bytes = new byte[Message.MAGIC.length];
        decode.readBytes(bytes);
        if (!Arrays.equals(bytes, Message.MAGIC)) {
            throw new IllegalArgumentException("魔数验证错误");
        }

        // 2. 读取消息类型
        byte messageType = decode.readByte();

        // 3. 读取消息体并反序列化
        byte[] body = new byte[decode.readableBytes()];
        decode.readBytes(body);

        if (Message.MessageType.REQUEST.getCode() == messageType) {
            return deserializeRequest(body);
        } else if (Message.MessageType.RESPONSE.getCode() == messageType) {
            return deserializeResponse(body);
        }

        throw new IllegalArgumentException("不支持的消息类型");
    }

    /**
     * 反序列化为 Request 对象
     */
    private Request deserializeRequest(byte[] body) {
        return JSONObject.parseObject(new String(body, StandardCharsets.UTF_8), Request.class, JSONReader.Feature.SupportClassForName);
    }

    /**
     * 反序列化为 Response 对象
     */
    private Response deserializeResponse(byte[] body) {
        return JSONObject.parseObject(new String(body, StandardCharsets.UTF_8), Response.class);
    }
}

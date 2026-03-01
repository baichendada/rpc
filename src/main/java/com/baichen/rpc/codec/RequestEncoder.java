package com.baichen.rpc.codec;

import com.alibaba.fastjson2.JSONObject;
import com.baichen.rpc.message.Message;
import com.baichen.rpc.message.Request;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;

/**
 * 请求消息编码器
 * <p>
 * 将 Request 对象编码为字节流，协议格式:
 * +----------+--------+----------+
 * |  Length  |  Magic |   Type   |  Body
 * +----------+--------+----------+
 * |   4B    |   6B   |   1B     |  N B
 * +----------+--------+----------+
 */
public class RequestEncoder extends MessageToByteEncoder<Request> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Request msg, ByteBuf out) throws Exception {
        // 1. 获取魔数和消息类型
        byte[] magic = Message.MAGIC;
        byte messageType = (byte) Message.MessageType.REQUEST.getCode();

        // 2. 序列化消息体为 JSON
        byte[] body = serializeMessage(msg);

        // 3. 计算总长度（不包括 Length 字段自己）
        int length = magic.length + Byte.BYTES + body.length;

        // 4. 按协议格式写入 ByteBuf
        out.writeInt(length);        // 4 字节：长度
        out.writeBytes(magic);       // 6 字节：魔数
        out.writeByte(messageType);  // 1 字节：消息类型
        out.writeBytes(body);       // N 字节：消息体
    }

    /**
     * 序列化 Request 对象为 UTF-8 字节数组
     */
    private static byte[] serializeMessage(Request msg) {
        return JSONObject.toJSONString(msg).getBytes(CharsetUtil.UTF_8);
    }
}

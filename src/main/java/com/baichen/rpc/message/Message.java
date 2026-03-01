package com.baichen.rpc.message;

import lombok.Data;
import lombok.Getter;

/**
 * 消息基础类，定义 RPC 通信协议的头部信息
 */
@Data
public class Message {
    /**
     * 魔数，用于标识这是一个 rpc-demo 协议的数据包
     */
    public static final byte[] MAGIC = "baichen".getBytes();

    private byte[] magic;

    private byte messageType;

    private byte[] body;

    /**
     * 消息类型枚举
     */
    @Getter
    public enum MessageType {
        /**
         * 请求消息
         */
        REQUEST(1),
        /**
         * 响应消息
         */
        RESPONSE(2),
        ;

        private final int code;

        MessageType(int code) {
            this.code = code;
        }
    }
}

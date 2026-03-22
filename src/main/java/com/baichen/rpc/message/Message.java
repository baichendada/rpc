package com.baichen.rpc.message;

import lombok.Data;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

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

    private short version;

    /**
     * Serialization And Compression Type
     * 上四位表示序列化类型，下四位表示压缩类型
     */
    private byte SACType;

    private byte[] body;

    /**
     * 消息类型枚举
     */
    @Getter
    public enum MessageType {
        /**
         * 请求消息
         */
        REQUEST(1, Request.class),
        /**
         * 响应消息
         */
        RESPONSE(2, Response.class),
        ;

        private static final Map<Byte, MessageType> CODE_2_TYPE = new HashMap<>();
        private static final Map<Class<?>, MessageType> CLASS_2_TYPE = new HashMap<>();
        private final byte code;
        private final Class<?> type;

        static {
            for (MessageType value : MessageType.values()) {
                if (CODE_2_TYPE.put(value.getCode(), value) != null) {
                    throw new RuntimeException("消息类型枚举值重复");
                }
                if (CLASS_2_TYPE.put(value.getType(), value) != null) {
                    throw new RuntimeException("消息类型枚举值重复");
                }
            }
        }

        MessageType(int code, Class<?> type) {
            this.code = (byte)code;
            this.type = type;
        }

        public static MessageType getByCode(byte code) {
            return CODE_2_TYPE.get(code);
        }

        public static MessageType getByType(Class<?> type) {
            return CLASS_2_TYPE.get(type);
        }
    }
}

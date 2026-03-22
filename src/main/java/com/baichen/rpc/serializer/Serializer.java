package com.baichen.rpc.serializer;

import lombok.Getter;

public interface Serializer {

    byte[] serialize(Object obj);

    <T> T deserialize(byte[] bytes, Class<T> clazz);

    @Getter
    enum SerializerType {
        JSON(1),
        HESSIAN(2),
        ;

        private final byte code;

        SerializerType(int code) {
            this.code = (byte) code;
        }
    }
}

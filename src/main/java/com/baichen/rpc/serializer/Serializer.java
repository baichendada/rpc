package com.baichen.rpc.serializer;

import com.baichen.rpc.spi.Extension;
import lombok.Getter;

public interface Serializer extends Extension {

    byte[] serialize(Object obj);

    <T> T deserialize(byte[] bytes, Class<T> clazz);
}

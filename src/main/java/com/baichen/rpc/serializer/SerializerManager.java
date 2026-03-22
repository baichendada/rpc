package com.baichen.rpc.serializer;

import java.util.HashMap;
import java.util.Map;

public class SerializerManager {

    private final Map<Byte, Serializer> serializerMap = new HashMap<>();

    public SerializerManager() {
        init();
    }

    private void init() {
        serializerMap.put(Serializer.SerializerType.JSON.getCode(), new JsonSerializer());
        serializerMap.put(Serializer.SerializerType.HESSIAN.getCode(), new HessianSerializer());
    }

    public Serializer getSerializerByCode(byte code) {
        return serializerMap.get(code);
    }
}

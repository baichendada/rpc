package com.baichen.rpc.serializer;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;

import java.nio.charset.StandardCharsets;

public class JsonSerializer implements Serializer {
    @Override
    public byte[] serialize(Object obj) {
        return JSONObject.toJSONString(obj).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        return JSONObject.parseObject(new String(bytes, StandardCharsets.UTF_8), clazz, JSONReader.Feature.SupportClassForName);
    }
}

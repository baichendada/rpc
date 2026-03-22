package com.baichen.rpc.serializer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

public class SerializerManager {

    private final Map<Byte, Serializer> codeMap = new HashMap<>();
    private final Map<String, Serializer> nameMap = new HashMap<>();

    public SerializerManager() {
        init();
    }

    private void init() {
        ServiceLoader<Serializer> loader = ServiceLoader.load(Serializer.class);
        for (Serializer serializer : loader) {
            if (serializer.getCode() < 0 || serializer.getCode() > 15) {
                throw new IllegalArgumentException("Serializer code must be between 0 and 15");
            }
            if (codeMap.put((byte) serializer.getCode(), serializer) != null) {
                throw new IllegalArgumentException("Duplicate serializer code: " + serializer.getCode());
            }
            if (nameMap.put(serializer.getName().toUpperCase(Locale.ROOT), serializer) != null) {
                throw new IllegalArgumentException("Duplicate serializer name: " + serializer.getName());
            }
        }
    }

    public Serializer getSerializer(byte code) {
        return codeMap.get(code);
    }
    public Serializer getSerializer(String name) {
        return nameMap.get(name);
    }
}

package com.baichen.rpc.compressor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

public class CompressorManager {
    private final Map<Byte, Compressor> codeMap = new HashMap<>();
    private final Map<String, Compressor> nameMap = new HashMap<>();

    public CompressorManager() {
        init();
    }

    private void init() {
        ServiceLoader<Compressor> loader = ServiceLoader.load(Compressor.class);
        for (Compressor compressor : loader) {
            if (compressor.getCode() < 0 || compressor.getCode() > 15) {
                throw new IllegalArgumentException("Compressor code must be between 0 and 15");
            }
            if (codeMap.put((byte) compressor.getCode(), compressor) != null) {
                throw new IllegalArgumentException("Duplicate compressor code: " + compressor.getCode());
            }
            if (nameMap.put(compressor.getName().toUpperCase(Locale.ROOT), compressor) != null) {
                throw new IllegalArgumentException("Duplicate compressor name: " + compressor.getName());
            }
        }
    }

    public Compressor getCompressor(byte code) {
        return codeMap.get(code);
    }
    public Compressor getCompressor(String name) {
        return nameMap.get(name);
    }

}

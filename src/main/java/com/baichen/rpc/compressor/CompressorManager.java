package com.baichen.rpc.compressor;

import java.util.HashMap;
import java.util.Map;

public class CompressorManager {
    private final Map<Byte, Compressor> compressorMap = new HashMap<>();

    public CompressorManager() {
        init();
    }

    private void init() {
        compressorMap.put(Compressor.CompressorType.NONE.getCode(), new NoneCompressor());
        compressorMap.put(Compressor.CompressorType.GZIP.getCode(), new GzipCompressor());
    }

    public Compressor getCompressorByCode(byte code) {
        return compressorMap.get(code);
    }

}

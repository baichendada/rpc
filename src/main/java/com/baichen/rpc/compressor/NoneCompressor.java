package com.baichen.rpc.compressor;

import java.util.Locale;

public class NoneCompressor implements Compressor {
    @Override
    public byte[] compress(byte[] data) {
        return data;
    }

    @Override
    public byte[] decompress(byte[] data) {
        return data;
    }

    @Override
    public String getName() {
        return "none";
    }

    @Override
    public int getCode() {
        return 0;
    }
}

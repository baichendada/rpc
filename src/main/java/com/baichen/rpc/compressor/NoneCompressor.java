package com.baichen.rpc.compressor;

public class NoneCompressor implements Compressor {
    @Override
    public byte[] compress(byte[] data) {
        return data;
    }

    @Override
    public byte[] decompress(byte[] data) {
        return data;
    }
}

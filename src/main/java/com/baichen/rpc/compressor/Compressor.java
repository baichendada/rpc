package com.baichen.rpc.compressor;

import lombok.Getter;

public interface Compressor {
    byte[] compress(byte[] data);
    byte[] decompress(byte[] data);

    @Getter
    enum CompressorType {
        NONE(1),
        GZIP(2),
        ;

        private final byte code;

        CompressorType(int code) {
            this.code = (byte) code;
        }
    }
}

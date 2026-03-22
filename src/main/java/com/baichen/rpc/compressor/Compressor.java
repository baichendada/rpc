package com.baichen.rpc.compressor;

import com.baichen.rpc.spi.Extension;

public interface Compressor extends Extension {
    byte[] compress(byte[] data);
    byte[] decompress(byte[] data);
}

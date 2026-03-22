package com.baichen.rpc.codec;

import com.baichen.rpc.compressor.CompressorManager;
import com.baichen.rpc.serializer.SerializerManager;
import io.netty.util.AttributeKey;

/**
 * 共享的 Channel 属性 Key，供 MessageEncoder 和 MessageDecoder 使用
 */
public final class ChannelAttributes {
    public static final AttributeKey<String> SERIALIZER_KEY = AttributeKey.valueOf("serializer_key");
    public static final AttributeKey<String> COMPRESSOR_KEY = AttributeKey.valueOf("compressor_key");
    public static final AttributeKey<SerializerManager> SERIALIZER_MANAGER = AttributeKey.valueOf("serializer_manager");
    public static final AttributeKey<CompressorManager> COMPRESSOR_MANAGER = AttributeKey.valueOf("compressor_manager");

    private ChannelAttributes() {}
}

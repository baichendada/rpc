package com.baichen.rpc.compressor;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class GzipCompressor implements Compressor {
    @Override
    public byte[] compress(byte[] data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        } catch (Exception e) {
            throw new RuntimeException("gzipCompress error", e);
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] decompress(byte[] data) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gzip.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("decompress error", e);
        }
    }
}

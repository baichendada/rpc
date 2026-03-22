package com.baichen.rpc.spi;

public interface Extension {

    String getName();

    default int getCode() {
        return -1;
    }
}

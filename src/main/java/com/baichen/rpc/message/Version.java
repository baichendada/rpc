package com.baichen.rpc.message;

import lombok.Getter;

@Getter
public enum Version {
    V1(1),
    ;
    private final short code;
    Version(int code) {
        this.code = (short) code;
    }
}

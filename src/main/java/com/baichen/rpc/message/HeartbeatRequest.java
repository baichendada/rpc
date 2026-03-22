package com.baichen.rpc.message;

import lombok.Data;

@Data
public class HeartbeatRequest {
    private final long requestTime = System.currentTimeMillis();
}

package com.baichen.rpc.message;

import lombok.Data;

@Data
public class HeartbeatResponse {
    private final long requestTime;

    public HeartbeatResponse(long requestTime) {
        this.requestTime = requestTime;
    }
}

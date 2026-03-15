package com.baichen.rpc.limiter;

public interface Limiter {

    boolean tryAcquire();

    default void release() {
        release(1);
    }

    void release(int count);
}

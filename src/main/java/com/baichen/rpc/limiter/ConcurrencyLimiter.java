package com.baichen.rpc.limiter;

import java.util.concurrent.Semaphore;

/**
 * 并发限流器
 * 只限制并发总量
 */
public class ConcurrencyLimiter implements Limiter {

    private final Semaphore semaphore;

    public ConcurrencyLimiter(int permits) {
        this.semaphore = new Semaphore(permits);
    }

    @Override
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    @Override
    public void release(int count) {
        semaphore.release(count);
    }
}

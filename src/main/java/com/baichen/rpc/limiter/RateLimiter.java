package com.baichen.rpc.limiter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 速率限流器
 * 通过计算出每秒最大请求数平均划分的ns数 x
 * x ns最多通过一个请求，这样限制每秒最大请求数
 */
public class RateLimiter implements Limiter {

    private final int MAX_ATTEMPTS = 32;

    private final long MAX_WAIT_DURATION = TimeUnit.MILLISECONDS.toNanos(500);

    private final long nsPerPermit;

    // 下次请求允许的时间
    private final AtomicLong nextPermitNs = new AtomicLong(0);

    public RateLimiter(Integer permitsPerSecond) {
        if (permitsPerSecond == null || permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive, got: " + permitsPerSecond);
        }
        if (permitsPerSecond > 1_000_000_000) {
            throw new IllegalArgumentException("permitsPerSecond too high (max 1 billion), got: " + permitsPerSecond);
        }
        this.nsPerPermit = TimeUnit.SECONDS.toNanos(1) / permitsPerSecond;
    }

    @Override
    public boolean tryAcquire() {
        long now = System.nanoTime();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            long pre = nextPermitNs.get();

            // 计算下一个可用的时间点
            long next = Math.max(now, pre) + nsPerPermit;

            // 检查是否超过最大等待时间
            if (next - now > MAX_WAIT_DURATION) {
                return false;
            }

            if (nextPermitNs.compareAndSet(pre, next)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void release(int count) {
    }
}

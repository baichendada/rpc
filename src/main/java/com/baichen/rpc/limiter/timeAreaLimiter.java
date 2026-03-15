package com.baichen.rpc.limiter;

import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 区间令牌桶限流器
 * 限制区间的令牌数，相当于速率限流
 * 限流精度越大，对于程序的压力越大，所以一般不使用
 */
@Deprecated
public class timeAreaLimiter implements Limiter {

    private final AtomicInteger tokens;

    // 使用 Netty 的全局事件执行器避免资源泄漏
    private static final GlobalEventExecutor refillEventLoop = GlobalEventExecutor.INSTANCE;

    private ScheduledFuture<?> scheduledFuture;

    public timeAreaLimiter(int permits) {
        this.tokens = new AtomicInteger(permits);
        this.scheduledFuture = refillEventLoop.scheduleAtFixedRate(() -> {
            // 使用 getAndSet 确保原子性
            tokens.getAndSet(permits);
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void destroy() {
        // 取消定时任务
        scheduledFuture.cancel(false);
    }

    @Override
    public boolean tryAcquire() {
        while (true) {
            int remain = tokens.get();
            if (remain <= 0) {
                return false;
            }

            if (tokens.compareAndSet(remain, remain - 1)) {
                return true;
            }
        }
    }

    @Override
    public void release(int count) {
        // 速率限流不需要release，因为每一秒会填充新的tokens
    }
}

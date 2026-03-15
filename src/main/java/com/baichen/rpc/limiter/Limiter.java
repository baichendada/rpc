package com.baichen.rpc.limiter;

/**
 * 限流器接口
 * <p>
 * 定义统一的限流 API，支持不同的限流策略实现。
 * </p>
 *
 * <h3>实现类</h3>
 * <ul>
 *   <li>{@link RateLimiter} - 速率限流器（令牌桶算法）</li>
 *   <li>{@link ConcurrencyLimiter} - 并发限流器（基于 Semaphore）</li>
 * </ul>
 *
 * <h3>语义说明</h3>
 * <ul>
 *   <li><b>ConcurrencyLimiter</b>: {@code tryAcquire()} 和 {@code release()} 必须成对调用</li>
 *   <li><b>RateLimiter</b>: {@code release()} 是 no-op（令牌自动恢复，无需手动释放）</li>
 * </ul>
 *
 * @see RateLimiter
 * @see ConcurrencyLimiter
 */
public interface Limiter {

    /**
     * 尝试获取一个许可
     *
     * @return true 表示获取成功，false 表示被限流拒绝
     */
    boolean tryAcquire();

    /**
     * 释放一个许可（默认实现）
     * <p>
     * 注意：对于 RateLimiter，此方法是 no-op。
     * </p>
     */
    default void release() {
        release(1);
    }

    /**
     * 释放指定数量的许可
     *
     * @param count 要释放的许可数量
     */
    void release(int count);
}

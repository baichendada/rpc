package com.baichen.rpc.limiter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 速率限流器（令牌桶算法）
 * <p>
 * 使用无锁 CAS 操作实现高性能的速率限流，限制每秒最大请求数。
 * </p>
 *
 * <h3>算法原理</h3>
 * <ol>
 *   <li>计算每个请求需要的时间间隔：nsPerPermit = 1秒 / permitsPerSecond</li>
 *   <li>维护下一个允许的时间点：nextPermitNs</li>
 *   <li>每次请求通过 CAS 将 nextPermitNs 推进 nsPerPermit 纳秒</li>
 * </ol>
 *
 * <h3>特性</h3>
 * <ul>
 *   <li><b>无锁并发</b>：使用 CAS 操作，无需加锁</li>
 *   <li><b>纳秒精度</b>：使用 System.nanoTime() 提供纳秒级时间精度</li>
 *   <li><b>等待限制</b>：超过 MAX_WAIT_DURATION (500ms) 的请求会被拒绝</li>
 *   <li><b>重试限制</b>：CAS 失败最多重试 MAX_ATTEMPTS (32) 次</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * 本类是线程安全的，多个线程可以并发调用 {@link #tryAcquire()}。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 限制每秒 100 个请求
 * RateLimiter limiter = new RateLimiter(100);
 * if (limiter.tryAcquire()) {
 *     // 处理请求
 * } else {
 *     // 被限流，拒绝请求
 * }
 * }</pre>
 *
 * @see Limiter
 */
public class RateLimiter implements Limiter {

    /**
     * CAS 操作最大重试次数
     * <p>
     * 在高并发场景下，如果 CAS 连续失败 32 次，说明竞争非常激烈，直接拒绝请求。
     * </p>
     */
    private final int MAX_ATTEMPTS = 32;

    /**
     * 最大等待时间（纳秒）
     * <p>
     * 如果下一个可用时间点距离现在超过 500ms，则拒绝请求。
     * 这避免了请求在队列中等待过久。
     * </p>
     */
    private final long MAX_WAIT_DURATION = TimeUnit.MILLISECONDS.toNanos(500);

    /**
     * 每个许可需要的时间间隔（纳秒）
     * <p>
     * 例如：permitsPerSecond = 100 时，nsPerPermit = 10,000,000 ns (10ms)
     * </p>
     */
    private final long nsPerPermit;

    /**
     * 下一个请求允许通过的时间点（纳秒时间戳）
     * <p>
     * 使用 AtomicLong 保证并发安全，通过 CAS 操作更新。
     * </p>
     */
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

    /**
     * 尝试获取一个许可
     * <p>
     * 使用 CAS 操作推进时间窗口，如果成功则返回 true，否则重试或拒绝。
     * </p>
     *
     * <h3>算法步骤</h3>
     * <ol>
     *   <li>读取当前时间 now 和下一个允许的时间点 pre</li>
     *   <li>计算新的时间点 next = max(now, pre) + nsPerPermit</li>
     *   <li>如果 next - now > MAX_WAIT_DURATION，拒绝请求</li>
     *   <li>使用 CAS 将 nextPermitNs 从 pre 更新为 next</li>
     *   <li>如果 CAS 成功，返回 true；失败则重试（最多 MAX_ATTEMPTS 次）</li>
     * </ol>
     *
     * @return true 表示获取成功，false 表示被限流拒绝
     */
    @Override
    public boolean tryAcquire() {
        long now = System.nanoTime();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            long pre = nextPermitNs.get();

            // 计算下一个可用的时间点
            // Math.max(now, pre) 确保时间不会倒退
            // + nsPerPermit 推进一个时间片
            long next = Math.max(now, pre) + nsPerPermit;

            // 检查是否超过最大等待时间
            // 如果等待时间过长，直接拒绝，避免请求堆积
            if (next - now > MAX_WAIT_DURATION) {
                return false;
            }

            // 使用 CAS 更新 nextPermitNs
            // 如果成功，表示当前线程获得了许可
            // 如果失败，说明有其他线程抢先更新了，需要重试
            if (nextPermitNs.compareAndSet(pre, next)) {
                return true;
            }
        }
        // 重试次数用尽，拒绝请求
        return false;
    }

    /**
     * 释放许可（no-op）
     * <p>
     * 对于速率限流器，令牌会随时间自动恢复，无需手动释放。
     * 此方法为空实现，仅为满足 {@link Limiter} 接口。
     * </p>
     *
     * @param count 要释放的许可数量（忽略）
     */
    @Override
    public void release(int count) {
        // 速率限流器不需要手动释放，令牌随时间自动恢复
    }
}

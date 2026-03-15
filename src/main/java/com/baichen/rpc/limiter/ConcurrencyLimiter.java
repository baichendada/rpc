package com.baichen.rpc.limiter;

import java.util.concurrent.Semaphore;

/**
 * 并发限流器
 * <p>
 * 基于 {@link Semaphore} 实现的并发限流器，限制同时处理的最大请求数。
 * </p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>限制客户端同时发起的最大请求数</li>
 *   <li>限制服务端同时处理的最大请求数</li>
 *   <li>保护资源不被过度并发访问</li>
 * </ul>
 *
 * <h3>重要提示</h3>
 * <p>
 * {@link #tryAcquire()} 和 {@link #release(int)} 必须成对调用，
 * 否则会导致许可泄漏或过度释放。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ConcurrencyLimiter limiter = new ConcurrencyLimiter(100);
 * if (limiter.tryAcquire()) {
 *     try {
 *         // 处理请求
 *     } finally {
 *         limiter.release();  // 必须释放！
 *     }
 * } else {
 *     // 被限流，拒绝请求
 * }
 * }</pre>
 *
 * @see Limiter
 * @see Semaphore
 */
public class ConcurrencyLimiter implements Limiter {

    /**
     * 内部信号量，用于控制并发数
     */
    private final Semaphore semaphore;

    /**
     * 构造函数
     *
     * @param permits 最大并发数
     */
    public ConcurrencyLimiter(int permits) {
        this.semaphore = new Semaphore(permits);
    }

    /**
     * 尝试获取一个许可
     * <p>
     * 如果当前并发数未达到上限，返回 true；否则返回 false。
     * </p>
     *
     * @return true 表示获取成功，false 表示被限流拒绝
     */
    @Override
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    /**
     * 释放指定数量的许可
     * <p>
     * <b>重要</b>：必须与 {@link #tryAcquire()} 成对调用，通常在 finally 块中调用。
     * </p>
     *
     * @param count 要释放的许可数量
     */
    @Override
    public void release(int count) {
        semaphore.release(count);
    }
}

package com.baichen.rpc.exception;

/**
 * 限流异常
 * <p>
 * 当 RPC 请求因触发限流策略而被拒绝时抛出此异常。
 * 此异常不应该触发重试（retry = false），因为重试同样会被限流拒绝。
 * </p>
 *
 * <h3>限流类型</h3>
 * <ul>
 *   <li><b>全局并发限流</b>：客户端同时处理的请求数超过限制</li>
 *   <li><b>单服务速率限流</b>：对某个服务的请求速率超过限制</li>
 * </ul>
 *
 * @see com.baichen.rpc.limiter.ConcurrencyLimiter
 * @see com.baichen.rpc.limiter.RateLimiter
 */
public class LimiterException extends RpcException {
    public LimiterException(String message) {
        super(message, false);  // 限流异常不应该重试
    }
}

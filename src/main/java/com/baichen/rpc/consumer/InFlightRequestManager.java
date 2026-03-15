package com.baichen.rpc.consumer;


import com.baichen.rpc.exception.LimiterException;
import com.baichen.rpc.exception.RpcException;
import com.baichen.rpc.limiter.ConcurrencyLimiter;
import com.baichen.rpc.limiter.Limiter;
import com.baichen.rpc.limiter.RateLimiter;
import com.baichen.rpc.message.Request;
import com.baichen.rpc.message.Response;
import com.baichen.rpc.registry.ServiceMateData;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 正在处理的请求管理器
 * <p>
 * 负责管理 RPC 请求的生命周期，包括：
 * <ul>
 *   <li>两级限流控制（全局并发限流 + 单服务速率限流）</li>
 *   <li>请求超时管理（基于 HashedWheelTimer）</li>
 *   <li>请求-响应匹配</li>
 *   <li>资源自动释放</li>
 * </ul>
 * </p>
 *
 * <h3>限流策略</h3>
 * <ul>
 *   <li><b>全局并发限流</b>：使用 {@link ConcurrencyLimiter} 限制客户端同时处理的最大请求数</li>
 *   <li><b>单服务速率限流</b>：使用 {@link RateLimiter} 限制每个服务的每秒请求数</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * 本类是线程安全的，所有公共方法可以被多个线程并发调用。
 *
 * <h3>资源管理</h3>
 * 使用完毕后需要调用 {@link #shutdown()} 方法释放资源（停止时间轮定时器）。
 *
 * @see ConcurrencyLimiter
 * @see RateLimiter
 * @see HashedWheelTimer
 */
@Slf4j
public class InFlightRequestManager {

    // 用于维护正在等待响应的请求，key为请求ID，value为对应的 CompletableFuture
    private final Map<Integer, CompletableFuture<Response>> inFlightRequestMap;

    private final HashedWheelTimer hashedWheelTimer;

    private final ConsumerProperties properties;

    private final Limiter globalLimiter;

    private final Map<ServiceMateData, Limiter> serviceLimiterMap;

    public InFlightRequestManager(ConsumerProperties properties) {
        this.properties = properties;
        this.inFlightRequestMap = new ConcurrentHashMap<>();
        this.hashedWheelTimer = new HashedWheelTimer(1, TimeUnit.SECONDS, 64);
        this.globalLimiter = new ConcurrencyLimiter(properties.getGlobalLimit());
        this.serviceLimiterMap = new ConcurrentHashMap<>();
    }

    /**
     * 将请求添加到在途请求列表，并设置超时和限流
     * <p>
     * 此方法负责：
     * <ol>
     *   <li>两级限流检查（全局并发 + 单服务速率）</li>
     *   <li>注册超时定时器</li>
     *   <li>注册完成回调（释放资源）</li>
     * </ol>
     * </p>
     *
     * <h3>资源释放机制</h3>
     * <p>
     * 无论请求成功、失败还是超时，资源都会在 {@code whenComplete} 回调中统一释放：
     * </p>
     * <ul>
     *   <li><b>正常完成</b>：响应到达 → {@code completeRequest} → 触发 {@code whenComplete} → 释放资源</li>
     *   <li><b>超时</b>：定时器触发 → {@code completeExceptionally} → 触发 {@code whenComplete} → 释放资源</li>
     *   <li><b>异常</b>：连接断开等 → {@code completeExceptionally} → 触发 {@code whenComplete} → 释放资源</li>
     * </ul>
     *
     * @param request RPC 请求
     * @param service 目标服务元数据
     * @param waitResponseTimeoutMs 等待响应超时时间（毫秒）
     * @return CompletableFuture，可用于异步获取响应
     */
    public CompletableFuture<Response> putRequest(Request request, ServiceMateData service, long waitResponseTimeoutMs) {
        CompletableFuture<Response> future = new CompletableFuture<>();

        // ========== 第一级：全局并发限流 ==========
        // 限制客户端同时发起的最大请求数，防止客户端资源耗尽
        if (!globalLimiter.tryAcquire()) {
            future.completeExceptionally(new LimiterException("全局并发限流 (当前限制: " + properties.getGlobalLimit() + ")"));
            return future;
        }

        // ========== 第二级：单服务速率限流 ==========
        // 为每个服务创建独立的速率限流器，限制对单个服务的每秒请求数
        Limiter limiter = serviceLimiterMap.computeIfAbsent(service, k -> new RateLimiter(properties.getServiceLimit()));
        if (!limiter.tryAcquire()) {
            // 速率限流失败，需要释放已获取的全局并发许可（避免资源泄漏）
            globalLimiter.release();
            future.completeExceptionally(new LimiterException("服务速率限流 (当前限制: " + properties.getServiceLimit() + " req/s)"));
            return future;
        }

        // ========== 添加请求到等待列表 ==========
        // 用于后续通过 requestId 匹配响应
        inFlightRequestMap.putIfAbsent(request.getRequestId(), future);

        // ========== 设置超时定时器 ==========
        // 如果在指定时间内没有收到响应，则触发超时异常
        // 注意：超时回调只负责标记 future 为异常完成，资源释放由 whenComplete 统一处理
        Timeout timeout = hashedWheelTimer.newTimeout((e) -> {
            if (inFlightRequestMap.remove(request.getRequestId()) != null) {
                // 从 map 中移除请求，并标记 future 为超时异常
                // 这会触发下面的 whenComplete 回调来释放资源
                future.completeExceptionally(new RpcException("RPC 调用超时，requestId: " + request.getRequestId()));
            }
        }, waitResponseTimeoutMs, TimeUnit.MILLISECONDS);

        // ========== 注册完成回调 ==========
        // 无论请求如何完成（成功/失败/超时），都会执行此回调来释放资源
        // 这是唯一的资源释放点，确保不会遗漏
        future.whenComplete((r, t) -> {
            // 记录日志
            if (t != null) {
                log.error("RPC 调用失败，requestId: {}, error: {}", request.getRequestId(), t.getMessage());
            } else {
                log.info("RPC 调用成功，requestId: {}, response: {}", request.getRequestId(), r);
            }

            // ========== 清理资源 ==========
            // 1. 从 map 中移除（幂等操作，超时场景下已被移除）
            inFlightRequestMap.remove(request.getRequestId());

            // 2. 取消超时定时器（如果还没触发）
            timeout.cancel();

            // 3. 释放全局并发限流器的许可
            //    这是关键！必须释放，否则会导致许可泄漏
            globalLimiter.release();

            // 4. 释放服务速率限流器的许可
            //    对于 RateLimiter，这是 no-op（令牌自动恢复）
            //    但为了代码对称性和接口一致性，仍然调用
            limiter.release();
        });

        return future;
    }

    /**
     * 清除指定服务的限流器
     * <p>
     * 当连接关闭时调用，释放该服务的 RateLimiter 实例。
     * 注意：此时可能仍有该服务的在途请求，但 {@code limiter.release()} 对 RateLimiter 是 no-op，不会有问题。
     * </p>
     *
     * @param service 服务元数据
     */
    public void clearChannel(ServiceMateData service) {
        serviceLimiterMap.remove(service);
    }

    public boolean completeRequest(int requestId, Response response) {
        CompletableFuture<Response> future = inFlightRequestMap.get(requestId);
        if (future == null) {
            log.warn("未找到正在等待的请求 (可能已超时或重复响应)，requestId: {}", requestId);
            return false;
        }
        log.info("收到响应: {}", response);
        return future.complete(response);
    }

    public boolean completeExceptionallyRequest(int requestId, Throwable throwable) {
        CompletableFuture<Response> future = inFlightRequestMap.get(requestId);
        if (future == null) {
            log.warn("未找到正在等待的请求 (可能已超时或重复响应)，requestId: {}", requestId);
            return false;
        }
        log.info("收到异常响应: {}", throwable.getMessage());
        return future.completeExceptionally(throwable);
    }

    /**
     * 关闭管理器，释放资源
     */
    public void shutdown() {
        log.info("关闭 InFlightRequestManager，正在清理资源...");
        // 停止时间轮定时器
        hashedWheelTimer.stop();
        // 清理所有未完成的请求
        inFlightRequestMap.forEach((requestId, future) -> {
            future.completeExceptionally(new RpcException("RPC 客户端正在关闭"));
        });
        inFlightRequestMap.clear();
        log.info("InFlightRequestManager 已关闭");
    }
}

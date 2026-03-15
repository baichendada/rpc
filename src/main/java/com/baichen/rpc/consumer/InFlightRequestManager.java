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

    public CompletableFuture<Response> putRequest(Request request, ServiceMateData service, long waitResponseTimeoutMs) {
        CompletableFuture<Response> future = new CompletableFuture<>();

        // 全局限流
        if (!globalLimiter.tryAcquire()) {
            future.completeExceptionally(new LimiterException("全局并发限流 (当前限制: " + properties.getGlobalLimit() + ")"));
            return future;
        }

        // 分channel限流
        Limiter limiter = serviceLimiterMap.computeIfAbsent(service, k -> new RateLimiter(properties.getServiceLimit()));
        if (!limiter.tryAcquire()) {
            // 速率限流失败，需要释放已获取的全局并发许可
            globalLimiter.release();
            future.completeExceptionally(new LimiterException("服务速率限流 (当前限制: " + properties.getServiceLimit() + " req/s)"));
            return future;
        }

        // 添加请求到等待列表
        inFlightRequestMap.putIfAbsent(request.getRequestId(), future);

        // 设置请求超时处理，如果在指定时间内没有收到响应，则从等待列表中移除并完成异常
        Timeout timeout = hashedWheelTimer.newTimeout((e) -> {
            if (inFlightRequestMap.remove(request.getRequestId()) != null) {
                future.completeExceptionally(new RpcException("RPC 调用超时，requestId: " + request.getRequestId()));
            }
        }, waitResponseTimeoutMs, TimeUnit.MILLISECONDS);

        // 添加完成回调，记录调用结果并清理等待列表
        future.whenComplete((r, t) -> {
            if (t != null) {
                log.error("RPC 调用失败，requestId: {}, error: {}", request.getRequestId(), t.getMessage());
            } else {
                log.info("RPC 调用成功，requestId: {}, response: {}", request.getRequestId(), r);
            }
            // 只有当请求确实存在于 map 中时才释放资源（避免重复释放）
            if (inFlightRequestMap.remove(request.getRequestId()) != null) {
                timeout.cancel();
                // 释放全局并发限制器的许可（关键修复：防止资源泄漏）
                globalLimiter.release();
            }
        });

        return future;
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

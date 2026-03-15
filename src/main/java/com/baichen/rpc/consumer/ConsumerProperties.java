package com.baichen.rpc.consumer;

import com.baichen.rpc.registry.ServiceRegistryConfig;
import lombok.Data;

/**
 * RPC 消费者配置
 */
@Data
public class ConsumerProperties {
    /** Netty worker 线程数 */
    private Integer workerThreadNum = 4;

    /** 连接超时时间（毫秒） */
    private Integer connectTimeoutMs = 5000;

    /** 等待响应超时时间（毫秒） */
    private Long waitResponseTimeoutMs = 3000L;

    /** 总超时时间（毫秒，包含重试） */
    private Long totalTimeoutMs = 10000L;

    /** 全局并发限制（同时处理的最大请求数）
     * 默认 100，适用于中等并发场景
     */
    private Integer globalLimit = 100;

    /** 单服务速率限制（每秒最大请求数）
     * 默认 3 req/s，这是一个保守值，适用于测试环境
     * 生产环境建议根据下游服务能力调整到 100-1000 req/s
     */
    private Integer serviceLimit = 3;

    private Long slowRequestThresholdMs = 2000L;

    private Double slowRequestRate = 0.5;

    /** 负载均衡策略：roundRobin（轮询）、random（随机） */
    private String loadBalancePolicy = "roundRobin";

    /** 重试策略：forkAll（并行重试）、failOver（故障转移）、retrySame（同一服务重试） */
    private String retryPolicy = "forkAll";

    /** 服务注册中心配置 */
    private ServiceRegistryConfig serviceRegistryConfig;
}

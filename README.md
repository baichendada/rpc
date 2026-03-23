# RPC Demo

一个基于 Netty 实现的简单 RPC 框架演示项目。

## 技术栈

- **Java 17**
- **Netty 4.1** - 高性能 NIO 网络框架
- **FastJSON2** - JSON 序列化/反序列化
- **Lombok** - 简化代码
- **Logback** - 日志框架
- **Curator** - Zookeeper 服务发现框架
- **Hessian 4.0.66** - 二进制序列化框架

## 项目结构

```
src/main/java/com/baichen/rpc/
├── api/                # 服务接口定义
│   └── Add.java        # 演示用加法服务接口
├── message/            # 消息实体
│   ├── Message.java    # 消息基类，定义协议头
│   ├── Request.java    # RPC 请求
│   └── Response.java  # RPC 响应
├── codec/              # 编解码器
│   ├── MessageDecoder.java   # 消息解码器
│   ├── RequestEncoder.java   # 请求编码器
│   └── ResponseEncoder.java  # 响应编码器
├── provider/           # 服务端
│   ├── ProviderServer.java   # RPC 服务端
│   ├── ProviderRegistry.java # 服务注册表
│   ├── ProviderProperties.java # 服务端配置
│   ├── AddImpl.java          # 服务实现类
│   └── ProviderApp.java     # 服务端启动入口
└── consumer/           # 客户端
    ├── ConsumerProxyFactory.java  # 动态代理工厂
    ├── ConsumerProperties.java # 客户端配置
    └── ConsumerApp.java    # 客户端启动入口
└── exception/          # 异常处理
    └── RpcException.java   # RPC 异常
└── registry/         # 服务注册与发现
    ├── ServiceRegistry.java       # 服务注册接口
    ├── ServiceRegistryManager.java # 注册中心管理器（SPI）
    ├── ServiceRegistryConfig.java # 注册中心配置
    ├── ServiceMateData.java       # 服务元数据
    ├── DefaultServiceRegistry.java # 注册中心代理（缓存容错）
    ├── ZookeeperServiceRegistry.java # Zookeeper实现
    └── RedisServiceRegistry.java  # Redis实现（未完成）
└── loaderbalance/    # 负载均衡
    ├── LoaderBalancer.java        # 负载均衡接口
    ├── LoaderBalancerManager.java # 负载均衡管理器（SPI）
    ├── RandomLoaderBalancer.java  # 随机负载均衡
    └── RoundRobinLoaderBalancer.java # 轮询负载均衡
├── retry/           # 重试机制
    ├── RetryPolicy.java          # 重试策略接口
    ├── RetryContext.java         # 重试上下文
    ├── RetrySamePolicy.java      # 同一服务重试（指数退避）
    ├── FailOverPolicy.java       # 故障转移重试
    └── ForkAllPolicy.java        # 并行重试所有服务
└── limiter/         # 限流器
    ├── Limiter.java              # 限流器接口
    ├── RateLimiter.java          # 速率限流器（令牌桶算法）
    ├── ConcurrencyLimiter.java   # 并发限流器
    └── timeAreaLimiter.java      # 时间窗口限流器（已废弃）
└── breaker/         # 熔断器
    ├── CircuitBreaker.java       # 熔断器接口
    ├── CircuitBreakerFactory.java # 熔断器工厂接口（SPI）
    ├── CircuitBreakerManager.java # 熔断器管理器
    ├── ResponseTimeCircuitBreaker.java # 基于响应时间的熔断器
    └── ResponseTimeCircuitBreakerFactory.java # 熔断器工厂实现
└── fallback/        # 降级机制
    ├── Fallback.java             # 降级接口
    ├── FallbackTag.java          # 降级实现类注解
    ├── CacheFallback.java        # 缓存降级（返回上次成功结果）
    ├── MockFallback.java         # Mock 降级（反射调用注解指定实现类）
    └── DefaultFallback.java      # 默认降级（缓存优先，缓存未命中走 Mock）
└── metrics/         # 指标数据
    └── MetricsData.java          # RPC 调用指标（成功/失败/耗时/结果）
└── serializer/      # 序列化
    ├── Serializer.java           # 序列化接口
    ├── SerializerManager.java    # 序列化器注册中心
    ├── JsonSerializer.java       # JSON 序列化（fastjson2）
    └── HessianSerializer.java    # Hessian 二进制序列化
└── compressor/      # 压缩
    ├── Compressor.java           # 压缩接口
    ├── CompressorManager.java    # 压缩器注册中心
    ├── NoneCompressor.java       # 直通（不压缩）
    └── GzipCompressor.java       # GZIP 压缩
```

## 通信协议

自定义二进制协议，格式如下：

```
+-----------+--------+--------+---------+---------+--------+
|  Length   |  Magic |  Type  | Version | SACType |  Body  |
+-----------+--------+--------+---------+---------+--------+
|   4B      |   6B   |   1B   |   2B    |   1B    |  N B   |
+-----------+--------+--------+---------+---------+--------+

- Length:  整个消息的长度（不包括 Length 字段本身）
- Magic:   魔数 "baichen"，用于协议校验
- Type:    消息类型 (1=Request, 2=Response, 3=HeartbeatRequest, 4=HeartbeatResponse)
- Version: 协议版本 (1=V1)
- SACType: 上四位=序列化类型，下四位=压缩类型
- Body:    消息体，按序列化类型编码
```

### 响应码

Response 消息体包含响应码：

| 响应码 | 含义 |
|--------|------|
| 200    | 成功 |
| 400    | 错误 |

### 协议处理流程

1. **编码器 (RequestEncoder/ResponseEncoder)**:
   - 序列化消息体为 JSON
   - 计算总长度 = Magic(6B) + Type(1B) + Body
   - 按顺序写入: Length(4B) + Magic(6B) + Type(1B) + Body

2. **解码器 (MessageDecoder)**:
   - LengthFieldBasedFrameDecoder 根据 Length 字段粘包处理
   - decode 方法手动读取并校验 Magic 和 Type
   - 反序列化 Body 为 Request/Response 对象

## SPI 可插拔机制

项目基于 Java `ServiceLoader` 实现 SPI 机制，支持运行时热插拔扩展组件。

### SPI 组件

| 组件 | 接口 | Manager | 配置文件 |
|------|------|---------|----------|
| 序列化器 | `Serializer` | `SerializerManager` | `META-INF/services/com.baichen.rpc.serializer.Serializer` |
| 压缩器 | `Compressor` | `CompressorManager` | `META-INF/services/com.baichen.rpc.compressor.Compressor` |
| 重试策略 | `RetryPolicy` | `RetryPolicyManager` | `META-INF/services/com.baichen.rpc.retry.RetryPolicy` |
| 负载均衡 | `LoaderBalancer` | `LoaderBalancerManager` | `META-INF/services/com.baichen.rpc.loaderbalance.LoaderBalancer` |
| 服务注册 | `ServiceRegistry` | `ServiceRegistryManager` | `META-INF/services/com.baichen.rpc.registry.ServiceRegistry` |
| 熔断器工厂 | `CircuitBreakerFactory` | `CircuitBreakerManager` | `META-INF/services/com.baichen.rpc.breaker.CircuitBreakerFactory` |

### 扩展方式

1. 实现对应接口
2. 添加 `@SpiTag("name")` 注解
3. 在 SPI 配置文件中注册

### 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `serializerType` | json | 序列化方式 (json/hessian) |
| `compressorType` | none | 压缩方式 (none/gzip) |
| `retryPolicy` | forkAll | 重试策略 (forkAll/failOver/retrySame) |
| `loadBalancePolicy` | roundRobin | 负载均衡 (roundRobin/random) |
| `circuitBreakerType` | responseTime | 熔断器类型 (responseTime) |

---

## 快速开始

### 编译项目

```bash
mvn compile
```

### 运行

1. 先启动服务端：

```bash
mvn exec:java -Dexec.mainClass="com.baichen.rpc.provider.ProviderApp"
```

2. 再启动客户端：

```bash
mvn exec:java -Dexec.mainClass="com.baichen.rpc.consumer.ConsumerApp"
```

### 输出示例

服务端输出：
```
RPC 服务端启动成功，监听端口: 8085
收到请求: Request(serviceName=com.baichen.rpc.api.Add, methodName=add, ...)
```

客户端输出：
```
调用 add(1, 3) = 4
```

## 工作原理

### 服务注册
- ProviderServer 通过 ProviderRegistry 注册服务实现
- 服务注册时保存接口类和方法反射调用的映射

### RPC 调用流程
1. ConsumerProxyFactory 创建动态代理，发起调用
2. 构建 Request 对象（包含服务名、方法名、参数类型、参数值）
3. 通过 Netty 发送到 ProviderServer
4. ProviderServer 根据服务名从注册表查找服务实例
5. 通过反射调用对应方法
6. 返回结果给 Consumer

### 关键类说明

| 类 | 职责 |
|---|---|
| ProviderRegistry | 服务注册表，管理接口与服务实例的映射 |
| InvokerInstance | 封装服务实例和接口类，通过反射调用方法 |
| ConsumerProxyFactory | 动态代理工厂，通过 Java 动态代理透明化 RPC 调用 |
| ConsumerProperties | 客户端配置（线程数、超时时间等） |
| ProviderProperties | 服务端配置（主机、端口、注册中心等） |

## 当前版本功能

- [x] 基于 Netty 的 NIO 通信
- [x] 自定义二进制协议（魔数校验）
- [x] JSON 序列化
- [x] 服务注册与发现（Zookeeper）
- [x] 反射调用服务端方法
- [x] Logback 日志框架
- [x] 响应码机制（成功/失败）
- [x] 错误处理与异常传递
- [x] 调用超时机制
- [x] 连接池管理
- [x] 动态代理支持
- [x] 服务注册中心（支持 Zookeeper/Redis）
- [x] 负载均衡（随机、轮询）
- [x] 重试机制（FailOver、RetrySame、ForkAll）
- [x] 限流器（速率限流、并发限流）
- [x] 限流器与 RPC 框架集成（全局并发限流 + 单服务速率限流）
- [x] 熔断器（基于响应时间的滑动窗口熔断）
- [x] 降级机制（缓存降级 + Mock 降级，支持 @FallbackTag 注解）
- [x] 可插拔序列化（JSON / Hessian）
- [x] 可插拔压缩（None / GZIP），消息体 ≤ 256 字节自动跳过压缩
- [x] SPI 可插拔机制（基于 Java ServiceLoader），支持序列化器/压缩器/重试策略/负载均衡器/注册中心/熔断器的热插拔
- [x] 泛化调用（GenericConsumer），支持通过 `$invoke` 调用任意服务方法，无需接口依赖
- [x] 统一编解码器（MessageEncoder/MessageDecoder），协议头新增版本号和序列化/压缩类型
- [x] 心跳机制（HeartbeatHandler + IdleStateHandler），支持双向心跳检测和空闲连接关闭
- [x] 流量统计（TrafficRecordHandler），Consumer/Provider 双端统计上下行流量，每 5 秒打印

## 限流策略

RPC 框架支持两级限流保护：

### 1. 全局并发限流
- **实现**: `ConcurrencyLimiter`（基于 Semaphore）
- **作用**: 限制客户端同时处理的最大请求数
- **默认值**: 100 个并发请求
- **配置**: `ConsumerProperties.globalLimit`

### 2. 单服务速率限流
- **实现**: `RateLimiter`（令牌桶算法，CAS 无锁）
- **作用**: 限制每个服务的每秒请求数
- **默认值**: 3 req/s（保守值，生产环境建议调整为 100-1000）
- **配置**: `ConsumerProperties.serviceLimit`

### 限流触发
当请求触发限流时，会抛出 `LimiterException` 异常，调用方可捕获并进行降级处理。

## 待完善功能

- [ ] Redis 注册中心实现
- [ ] 限流器配置动态化（支持运行时调整）
- [ ] 降级策略配置化（支持运行时切换）
- [ ] 多种熔断器实现（基于异常频率、混合策略等）

---

## 更新日志

详细更新内容请查看 [CHANGELOG.md](./CHANGELOG.md)

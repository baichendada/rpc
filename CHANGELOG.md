# 更新日志

## [v0.15] - 2026-03-22

### 新增功能
- **心跳机制 (Heartbeat)**：新增客户端-服务端双向心跳检测，保持连接活跃
  - **`HeartbeatRequest`**：心跳请求消息，包含 `requestTime` 时间戳
  - **`HeartbeatResponse`**：心跳响应消息，回显 `requestTime` 用于计算延迟
  - **`HeartbeatHandler`**：心跳处理器，处理心跳请求/响应
  - **`IdleStateHandler`**：空闲检测，30秒读空闲关闭连接，5秒写空闲发送心跳
  - **MessageType 扩展**：新增 `HEARTBEAT_REQUEST(3)` 和 `HEARTBEAT_RESPONSE(4)` 消息类型
- **ConnectionManager 心跳集成**：Consumer 端 ChannelPipeline 集成 HeartbeatHandler

---

## [v0.14] - 2026-03-22

### 新增功能
- **可插拔序列化**：新增 `Serializer` 接口及两种实现
  - `JsonSerializer`：基于 fastjson2，默认序列化方式
  - `HessianSerializer`：基于 Caucho Hessian 4.0.66，二进制序列化，性能更好
  - `SerializerManager`：序列化器注册中心，通过 code 查找实现
- **可插拔压缩**：新增 `Compressor` 接口及两种实现
  - `NoneCompressor`：直通（不压缩），默认压缩方式
  - `GzipCompressor`：GZIP 压缩，适用于大消息体
  - `CompressorManager`：压缩器注册中心，通过 code 查找实现
- **统一编解码器**：新增 `MessageEncoder`，替换原有的 `RequestEncoder` 和 `ResponseEncoder`
- **协议版本字段**：新增 `Version` 枚举，协议头增加 2 字节版本号
- **协议升级**：协议头新增 `Version(2B)` 和 `SACType(1B)` 字段
  - `SACType` 上四位 = 序列化类型，下四位 = 压缩类型
  - 消息体 ≤ 256 字节时自动跳过压缩，使用 NONE 压缩码
- **配置项新增**：`ConsumerProperties` 和 `ProviderProperties` 新增 `serializerType`、`compressorType` 字段

### 代码修复
- **压缩码 bug 修复** ⚠️ **CRITICAL**：小消息体原先将压缩码置为 0（未注册），导致解码端找不到压缩器而报错；修复为写入 `NONE.getCode()`
- **GzipCompressor 资源泄漏修复** ⚠️ **IMPORTANT**：`GZIPOutputStream`/`GZIPInputStream` 未关闭，导致 native Deflater/Inflater 内存泄漏；改用 try-with-resources 正确关闭
- **HessianSerializer 异常处理修复**：序列化失败时原先返回空字节数组，导致下游静默失败；改为抛出 `RuntimeException`

### 架构改进
- **ChannelAttributes 共享类**：提取 `AttributeKey` 常量到 `ChannelAttributes`，消除 `MessageEncoder`/`MessageDecoder` 重复定义，避免字符串 key 不一致的隐患

### 依赖更新
- 新增 `com.caucho:hessian:4.0.66`

---

## [v0.13] - 2026-03-22

### 新增功能
- **降级机制 (Fallback)**：RPC 调用失败时自动降级，避免异常扩散
  - **`Fallback` 接口**：定义 `fallback()` 和 `recordMetrics()` 两个方法
  - **`CacheFallback`**：缓存上次成功调用的返回值，降级时返回缓存结果（以方法+参数为 key）
  - **`MockFallback`**：通过 `@FallbackTag` 注解找到降级实现类，反射调用对应方法
  - **`DefaultFallback`**：链式降级策略，优先走缓存，缓存未命中再走 Mock
  - **`@FallbackTag` 注解**：标注在服务接口上，指定降级实现类
  - **`AddFallbackImpl`**：演示用降级实现，add/minus 均返回 0

### 集成变更
- **ConsumerProxyFactory 集成降级**：
  - 所有节点均被熔断（`decideService` 抛异常）时，触发降级
  - RPC 调用成功后，通过 `fallback.recordMetrics()` 更新缓存
  - 重试全部失败后，触发降级而非直接抛出异常

### 代码修复
- **MetricsData 新增 `result` 字段**：`complete(Object result)` 保存调用结果，供 `CacheFallback` 使用
- **doRetry lambda 熔断器修复** ⚠️ **CRITICAL**：重试 lambda 中使用 `retryService` 的熔断器而非原始 `service` 的熔断器

### 包结构修复
- **MetricsData 包迁移**：从 `com.baichen.metrics` 迁移到 `com.baichen.rpc.metrics`，统一包结构

---

## [v0.12] - 2026-03-16

### 新增功能
- **熔断器 (Circuit Breaker)**：新增基于响应时间的熔断器实现
  - **滑动窗口统计**：使用 10 秒滑动窗口，1 秒为一个槽位，统计请求成功/失败比例
  - **三种状态**：CLOSED（正常）、OPEN（熔断）、HALF_OPEN（半开）
  - **可配置参数**：
    - `slowRequestThresholdMs`: 慢请求阈值（默认 1000ms）
    - `slowRequestRatioThreshold`: 慢请求比例阈值（默认 50%）
    - `minRequestCountThreshold`: 最小请求数阈值（默认 5）
    - `breakMs`: 熔断持续时间（默认 5000ms）
  - **熔断触发条件**：滑动窗口内慢请求比例超过阈值且请求数达到最小阈值
  - **半开探测**：熔断 5 秒后进入半开状态，允许少量请求探测服务是否恢复
- **MetricsData**：新增指标数据类，用于记录 RPC 调用的成功/失败/耗时信息

### 代码修复
- **RateLimiter 限流阈值修复** ⚠️ **CRITICAL**
  - **问题**：MAX_WAIT_DURATION 为 500ms，对于 3 req/s 的低速率限流，只能容纳约 1-2 个并发请求
  - **修复**：将 MAX_WAIT_DURATION 从 500ms 调整为 1s，支持适度并发
  - **问题**：MAX_ATTEMPTS 为 32，高并发下 CAS 竞争激烈时重试次数不足
  - **修复**：将 MAX_ATTEMPTS 从 32 调整为 512

- **ResponseTimeCircuitBreaker 统计逻辑修复** ⚠️ **CRITICAL**
  - **问题**：`processClosedState` 中慢请求会 `decrementAndGet` requestCount，导致 totalRequestCount 可能为负数
  - **修复**：无论成功还是失败请求，都先 `incrementAndGet` requestCount，再对失败请求增加 failedCount

### 集成变更
- **ConsumerProxyFactory 集成熔断器**：
  - 服务选择前通过 `circuitBreaker.allowRequest()` 检查服务是否可用
  - 熔断时跳过不可用服务，继续尝试其他服务
  - RPC 调用完成后通过 `circuitBreaker.recordRpc()` 记录指标

### 架构改进
- **CircuitBreaker 接口重构**：
  - `allowRequest()` 方法参数从 `ServiceMateData service` 改为无参数
  - `recordRpc()` 方法参数从 `ServiceMateData service, MetricsData metricsData` 改为只接收 `MetricsData`
  - 熔断器状态管理内部化，不再依赖外部传入 service 参数

---

## [v0.11] - 2026-03-15

### 关键修复（再次修复）
- **修复 InFlightRequestManager 资源释放逻辑** ⚠️ **CRITICAL**
  - 移除条件判断，改为无条件释放资源
  - **问题**：超时回调只标记 future 为异常，不释放资源；whenComplete 通过条件判断跳过资源释放
  - **修复**：whenComplete 无论何种完成方式都释放资源
  - 感谢用户指出：超时场景下限流器、定时任务等资源未释放的问题

### 代码改进
- **为核心类添加详细注释**：
  - `Limiter` 接口：说明不同实现的语义差异
  - `RateLimiter`：详细说明令牌桶算法原理、CAS 操作流程、线程安全性
  - `ConcurrencyLimiter`：说明使用场景和 acquire/release 成对调用的重要性
  - `InFlightRequestManager.putRequest()`：逐步注释两级限流、超时处理、资源释放机制
  - `ProviderServer.LimiterServerHandler`：详细注释请求 ID 跟踪机制和许可释放时机

### 代码改进
- **RpcException 重构**：
  - 将 retry 字段改为 final，通过构造函数传递
  - 遵循 Java 不可变对象最佳实践，线程安全
  - LimiterException 通过 super(message, false) 传递 retry 标志

- **异常处理改进**：
  - doRetry() 方法直接抛出底层 RpcException 而非 ExecutionException
  - TimeoutException 包装原始异常，保留异常链
  - 更清晰的异常传播路径

- **Provider 限流器文档**：
  - 为 LimiterServerHandler 添加详细 JavaDoc
  - 说明两级限流策略和请求 ID 跟踪机制
  - 添加空指针检查和错误日志

### 架构改进
- **Consumer 端**：
  - Bootstrap 创建逻辑移至 ConnectionManager（职责更清晰）
  - EventLoopGroup 生命周期由 ConnectionManager 管理
  - 支持优雅关闭（shutdown 方法）

- **Provider 端**：
  - 全局 serviceLimiter 替代 per-connection limiter
  - 使用 Set 跟踪请求 ID 而非计数器（更可靠）
  - channelInactive 时释放所有未完成请求的许可

---

## [v0.10] - 2026-03-15

### 新增功能
- **限流器集成到 RPC 框架**：将限流功能正式集成到消费者端
  - 全局并发限流：使用 `ConcurrencyLimiter` 限制客户端同时处理的最大请求数（默认 100）
  - 单服务速率限流：使用 `RateLimiter` 限制每个服务的每秒请求数（默认 3 req/s）
  - 触发限流时抛出 `LimiterException` 异常，调用方可捕获并处理
- **InFlightRequestManager**：新增请求管理器类，统一管理请求生命周期
  - 封装请求超时管理（HashedWheelTimer）
  - 封装限流逻辑
  - 提供 `shutdown()` 方法释放资源
- **LimiterException**：新增限流异常类，区分全局限流和单服务限流

### 代码重构
- **ConsumerProxyFactory 重构**：将请求管理逻辑从 `ConsumerProxyFactory` 抽离到 `InFlightRequestManager`
  - 移除静态的 `IN_FLIGHT_REQUEST_MAP`
  - 将 `ConsumerChannelHandler` 从 static 改为 inner class，可访问 `InFlightRequestManager`
  - 简化 `callRpcAsync` 方法，超时和限流逻辑统一由 `InFlightRequestManager` 处理

### 文档更新
- **JavaDoc 完善**：为 `InFlightRequestManager` 和 `LimiterException` 添加详细文档
- **配置注释**：为 `ConsumerProperties` 添加字段注释，说明默认值和推荐配置
- **限流策略说明**：在 JavaDoc 中详细说明两级限流策略和线程安全性

### 配置变更
- **ConsumerProperties 新增字段**：
  - `globalLimit`: 全局并发限制（默认 100）
  - `serviceLimit`: 单服务速率限制（默认 3 req/s，生产环境建议调整为 100-1000）

---

## [v0.9] - 2026-03-15

### 新增功能
- **限流器抽象**：新增 `Limiter` 接口，定义统一的限流 API
- **速率限流器 (RateLimiter)**：基于令牌桶算法的速率限流器
  - 使用 CAS 无锁并发控制，纳秒级精度
  - 支持最大等待时间配置（默认 500ms）
  - 最大重试次数限制（32 次）
- **并发限流器 (ConcurrencyLimiter)**：基于 Semaphore 的并发限流器
  - 限制同时处理的最大请求数
  - 支持 acquire/release 语义
- **时间窗口限流器 (timeAreaLimiter)**：基于时间窗口的令牌桶限流器（已标记 @Deprecated）

### 代码修复
- **RateLimiter CAS 逻辑修复**：修复严重的并发 bug
  - **原实现**: `Math.max(now + nsPerPermit, pre)` 在 `pre > now` 时会导致时间窗口无法推进
  - **修复后**: `Math.max(now, pre) + nsPerPermit` 确保每次都正确推进时间窗口
- **RateLimiter 参数校验**：新增构造函数参数验证
  - 拒绝 null、零或负数的 `permitsPerSecond`
  - 限制最大值为 10 亿（防止整数溢出）
- **timeAreaLimiter 资源泄漏修复**：
  - 使用 `GlobalEventExecutor.INSTANCE` 替代自定义 `DefaultEventLoop`，避免线程池泄漏
  - 使用 `getAndSet` 替代 `set`，修复令牌填充的竞态条件

### 代码优化
- **并发安全性增强**：所有限流器实现均通过原子操作保证线程安全
- **性能优化**：RateLimiter 最大重试次数从 512 降低到 32，减少 CPU 空转

### 技术细节
- **算法**: 令牌桶算法（Token Bucket）
- **并发控制**: CAS (Compare-And-Swap) 无锁编程
- **时间精度**: 纳秒级 (System.nanoTime())

---

## [v0.8] - 2026-03-15

### 新增功能
- **重试机制**：新增 `RetryPolicy` 接口及多种实现
- **RetrySamePolicy**：同一服务重试，使用指数退避策略（100ms, 200ms, 400ms）
- **FailOverPolicy**：故障转移重试，失败后尝试其他服务节点
- **ForkAllPolicy**：并行重试所有服务，任一成功即返回
- **RetryContext**：重试上下文，封装重试所需信息

### 代码优化
- **Consumer 客户端重构**：
  - 使用 HashedWheelTimer 替代简单定时器，更高效的超时管理
  - 新增 `callRpcAsync` 方法实现异步 RPC 调用
  - 新增 `totalTimeoutMs` 总超时配置
  - 新增 `retryPolicy` 重试策略配置
- **FailOverPolicy 修复**：修复不可变列表导致 `UnsupportedOperationException` 的 bug

### 依赖更新
- 新增 `netty` 内置的 `HashedWheelTimer` 依赖

---

## [v0.7] - 2026-03-11

### 新增功能
- **负载均衡**：新增 `LoaderBalancer` 接口及实现类
- **随机负载均衡**：`RandomLoaderBalancer` 随机选择服务节点
- **轮询负载均衡**：`RoundRobinLoaderBalancer` 轮询选择服务节点

### 代码优化
- **配置化负载均衡**：通过 `ConsumerProperties.loadBalancePolicy` 配置负载均衡策略
- **Balancer 实例共享**：将 LoaderBalancer 提升为 Factory 实例变量，确保多代理共享计数器
- **Handler 抽离**：将匿名内部类改为具名类 `ConsumerChannelHandler`

---

## [v0.6] - 2026-03-11

### 新增功能
- **Zookeeper 服务注册中心**：基于 Curator 框架实现服务发现
- **服务注册接口**：新增 `ServiceRegistry` 接口及配置类
- **服务元数据**：`ServiceMateData` 存储服务名、主机、端口信息
- **配置类**：新增 `ConsumerProperties` 和 `ProviderProperties` 配置类

### 代码优化
- **动态服务发现**：Consumer 从注册中心获取服务地址，而非硬编码
- **缓存容错**：`DefaultServiceRegistry` 本地缓存，注册中心不可用时使用缓存
- **服务注册自动化**：ProviderServer 启动时自动注册服务
- **代码重构**：拆分 `ConsumerProxyFactory.invoke()` 为多个私有方法
- **命名规范**：统一使用 `Registry` 替代 `Register`

### 依赖更新
- 新增 `curator-x-discovery 5.9.0` 依赖

---

## [v0.5] - 2026-03-05

### 新增功能
- **动态代理支持**：新增 `ConsumerProxyFactory` 动态代理工厂类
- **minus 方法**：Add 接口新增 minus 方法作为演示

### 代码优化
- **RPC 调用透明化**：通过 Java 动态代理，用户无需关心 RPC 调用细节，只需调用接口方法即可
- **Consumer 优化**：Response 直接返回结果，错误处理逻辑优化

---

## [v0.4] - 2026-03-05

### 新增功能
- **连接池**：新增 `ConnectionManager` 连接管理器，维护连接池避免重复创建连接
- **请求ID机制**：Request/Response 增加 `requestId` 字段，用于匹配请求响应

### 代码优化
- **Consumer 优化**：集成连接池，复用连接而非每次创建新连接
- **ProviderServer 优化**：响应时携带 requestId，移除请求后关闭连接

---

## [v0.3] - 2026-03-03

### 新增功能
- **日志框架**：集成 Logback，提供统一的日志输出
- **异常处理**：新增 `RpcException` 异常类
- **响应码机制**：Response 消息增加响应码（200=成功，400=错误）
- **调用超时**：Consumer 客户端增加 3 秒调用超时机制

### 代码优化
- **Handler 抽离**：将 `ProviderServer` 中的业务处理逻辑抽离为独立的内部类 `ProviderServerHandler`
- **异常处理增强**：
  - 服务端增加服务未找到的错误处理
  - 增加 channel 生命周期日志（active/inactive）
  - 增加异常捕获与处理

### 依赖更新
- 新增 `logback-classic 1.2.10` 依赖

---

## [v0.2] - 2026-03-02

### 新增功能
- 服务注册与发现机制
- 反射调用服务端方法

---

## [v0.1] - 2026-03-01

### 初始版本
- 基于 Netty 的 NIO 通信
- 自定义二进制协议（魔数校验）
- JSON 序列化

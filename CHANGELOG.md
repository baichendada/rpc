# 更新日志

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

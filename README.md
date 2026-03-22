# RPC Demo

一个基于 Netty 实现的简单 RPC 框架演示项目。

## 技术栈

- **Java 17**
- **Netty 4.1** - 高性能 NIO 网络框架
- **FastJSON2** - JSON 序列化/反序列化
- **Lombok** - 简化代码
- **Logback** - 日志框架
- **Curator** - Zookeeper 服务发现框架

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
    ├── ServiceRegistryConfig.java # 注册中心配置
    ├── ServiceMateData.java       # 服务元数据
    ├── DefaultServiceRegistry.java # 注册中心代理（缓存容错）
    ├── ZookeeperServiceRegistry.java # Zookeeper实现
    └── RedisServiceRegistry.java  # Redis实现（未完成）
└── loaderbalance/    # 负载均衡
    ├── LoaderBalancer.java        # 负载均衡接口
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
└── fallback/        # 降级机制
    ├── Fallback.java             # 降级接口
    ├── FallbackTag.java          # 降级实现类注解
    ├── CacheFallback.java        # 缓存降级（返回上次成功结果）
    ├── MockFallback.java         # Mock 降级（反射调用注解指定实现类）
    └── DefaultFallback.java      # 默认降级（缓存优先，缓存未命中走 Mock）
└── metrics/         # 指标数据
    └── MetricsData.java          # RPC 调用指标（成功/失败/耗时/结果）
```

## 通信协议

自定义二进制协议，格式如下：

```
+-----------+--------+--------+----------+
|  Length   |  Magic |  Type  |   Body   |
+-----------+--------+--------+----------+
|   4B     |   6B   |   1B   |   N B    |
+-----------+--------+--------+----------+

- Length:  整个消息的长度（不包括 Length 字段本身）
- Magic:   魔数 "baichen"，用于协议校验
- Type:    消息类型 (1=Request, 2=Response)
- Body:    消息体，JSON 格式
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

- [ ] 多种序列化方式（Hessian、Protobuf）
- [ ] Redis 注册中心实现
- [ ] 限流器配置动态化（支持运行时调整）
- [ ] 降级策略配置化（支持运行时切换）

---

## 更新日志

详细更新内容请查看 [CHANGELOG.md](./CHANGELOG.md)

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
- [x] 调用超时机制（3秒）
- [x] 连接池管理
- [x] 动态代理支持
- [x] 服务注册中心（支持 Zookeeper/Redis）

## 待完善功能

- [ ] 负载均衡
- [ ] 重试机制
- [ ] 多种序列化方式（Hessian、Protobuf）
- [ ] 服务治理（熔断、限流）
- [ ] Redis 注册中心实现

---

## 更新日志

详细更新内容请查看 [CHANGELOG.md](./CHANGELOG.md)

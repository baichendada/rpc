# RPC Demo

一个基于 Netty 实现的简单 RPC 框架演示项目。

## 技术栈

- **Java 17**
- **Netty 4.1** - 高性能 NIO 网络框架
- **FastJSON2** - JSON 序列化/反序列化
- **Lombok** - 简化代码

## 项目结构

```
src/main/java/com/baichen/rpc/
├── message/           # 消息实体
│   ├── Message.java   # 消息基类，定义协议头
│   ├── Request.java   # RPC 请求
│   └── Response.java # RPC 响应
├── codec/            # 编解码器
│   ├── MessageDecoder.java   # 消息解码器
│   ├── RequestEncoder.java   # 请求编码器
│   └── ResponseEncoder.java  # 响应编码器
├── provider/         # 服务端
│   ├── ProviderServer.java   # RPC 服务端
│   └── ProviderApp.java      # 服务端启动入口
└── consumer/         # 客户端
    ├── Consumer.java        # RPC 客户端
    └── ConsumerApp.java     # 客户端启动入口
```

## 通信协议

自定义二进制协议，格式如下：

```
+----------+--------+----------+
|  Length  |  Magic |   Type   |  Body
+----------+--------+----------+
|   4B    |   6B   |   1B     |  N B
+----------+--------+----------+

- Length:  整个消息的长度（不包含 Length 字段本身）
- Magic:   魔数 "baichen"，用于协议校验
- Type:    消息类型 (1=Request, 2=Response)
- Body:    消息体，JSON 格式
```

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
收到请求: Request(serviceName=test, methodName=add, ...)
```

客户端输出：
```
调用 add(1, 3) = 1
调用 add(12, 3) = 1
```

## 当前版本功能

- [x] 基于 Netty 的 NIO 通信
- [x] 自定义二进制协议（魔数校验）
- [x] JSON 序列化
- [x] 简单的请求-响应模型

## 待完善功能

- [ ] 反射调用服务端方法（目前硬编码返回 1）
- [ ] 服务注册与发现
- [ ] 多种序列化方式（Hessian、Protobuf）
- [ ] 负载均衡
- [ ] 超时与重试机制
- [ ] 连接池

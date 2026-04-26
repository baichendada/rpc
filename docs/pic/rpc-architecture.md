# RPC 框架架构图

```mermaid
graph TB
    subgraph Consumer["Consumer Side"]
        direction TB
        App["ConsumerApp"]
        Proxy["ConsumerProxyFactory\n(动态代理 InvocationHandler)"]

        subgraph Governance["服务治理层"]
            direction LR
            Reg["DefaultServiceRegistry\n先查ZK · ZK故障用缓存兜底"]
            LB["LoaderBalancerManager SPI\nRandom / RoundRobin"]
            CB["CircuitBreakerManager\n滑动窗口10槽 · CLOSED/OPEN/HALF_OPEN"]
            Retry["RetryPolicyManager SPI\nRetrySame / FailOver / ForkAll"]
            Fallback["DefaultFallback\nCacheFallback → MockFallback(@FallbackTag)"]
        end

        subgraph ConnMgr["连接与请求管理层"]
            direction LR
            CM["ConnectionManager\nhost:port → Channel 池"]
            IFRM["InFlightRequestManager\nrequestId→Future\n两级限流 · HashedWheelTimer超时"]
        end

        subgraph CPipeline["Consumer Netty Pipeline"]
            direction LR
            CT["TrafficRecordHandler\n流量统计"]
            CC["MessageEncoder/Decoder\n序列化+压缩"]
            CI["IdleStateHandler\n读空闲30s/写空闲5s"]
            CH["HeartbeatHandler\n写空闲发Ping"]
            CA["ConsumerChannelHandler\ncompleteRequest()"]
        end
    end

    subgraph Registry["Registry Side"]
        ZK["ZookeeperServiceRegistry\n/rpc/{serviceName}/{host:port}\nProvider启动写入 · Consumer启动读取"]
    end

    subgraph Provider["Provider Side"]
        direction TB
        subgraph PEntry["入口层"]
            direction LR
            PApp["ProviderApp"]
            PServer["ProviderServer\nNetty ServerBootstrap"]
            PReg["ProviderRegistry\nserviceName → 实现类"]
        end

        subgraph PPipeline["Provider Netty Pipeline"]
            direction LR
            PT["TrafficRecordHandler\n流量统计"]
            PD["MessageDecoder/Encoder\n解压+反序列化"]
            PI["IdleStateHandler\n读空闲30s/写空闲5s"]
            PH["HeartbeatHandler\n收Ping回Pong"]
            PL["LimiterServerHandler\n全局并发+单Channel速率"]
            PS["ProviderServerHandler\ninvokerThreadPool反射调用"]
        end

        Impl["AddImpl\n业务实现类"]
    end

    subgraph Infra["公共基础设施 SPI 可插拔"]
        direction LR
        subgraph Ser["序列化"]
            SM["SerializerManager"]
            J["JsonSerializer\n@code=1"]
            H["HessianSerializer\n@code=2"]
        end
        subgraph Comp["压缩"]
            CM2["CompressorManager"]
            N["NoneCompressor\n@code=1"]
            G["GzipCompressor\n@code=2"]
        end
        SPI["SPI机制\n@SpiTag + Extension\nServiceLoader\nMETA-INF/services/"]
    end

    %% Consumer 内部流向
    App --> Proxy
    Proxy --> Reg
    Proxy --> LB
    Proxy --> CB
    Proxy --> IFRM
    Proxy --> CM
    Proxy --> Retry
    Proxy --> Fallback

    %% Consumer Pipeline 顺序
    CM --> CT --> CC --> CI --> CH --> CA
    CA --> IFRM

    %% Registry 交互
    Reg -->|"正常查询"| ZK
    PServer -->|"启动时注册"| ZK

    %% 网络传输
    CC <-->|"TCP 字节流"| PT

    %% Provider Pipeline 顺序
    PT --> PD --> PI --> PH --> PL --> PS
    PS --> PReg
    PS --> Impl

    %% 基础设施
    CC --> SM
    CC --> CM2
    PD --> SM
    PD --> CM2
    SM --> J
    SM --> H
    CM2 --> N
    CM2 --> G
    SM --> SPI
    CM2 --> SPI

    %% 样式
    style Consumer fill:#EEF5FF,stroke:#5588CC
    style Registry fill:#F0FFF0,stroke:#44AA44
    style Provider fill:#FFF4E8,stroke:#CC8833
    style Infra fill:#F5F0FF,stroke:#8866CC
    style Governance fill:#DDEEFF,stroke:#5588CC
    style ConnMgr fill:#DDEEFF,stroke:#5588CC
    style CPipeline fill:#DDEEFF,stroke:#5588CC
    style PEntry fill:#FFE8CC,stroke:#CC8833
    style PPipeline fill:#FFE8CC,stroke:#CC8833
    style Ser fill:#EDE8FF,stroke:#8866CC
    style Comp fill:#EDE8FF,stroke:#8866CC
```

## 协议帧格式

```
┌──────────┬──────────┬────────┬───────────┬───────────┬────────┐
│ Length   │ Magic    │ Type   │ Version   │ SACType   │ Body   │
│  (4B)    │  (7B)    │  (1B)  │   (2B)    │   (1B)    │  (NB)  │
└──────────┴──────────┴────────┴───────────┴───────────┴────────┘
Magic = "baichen"    Type: 1=Request  2=Response
SACType 高4位=序列化类型  低4位=压缩类型    ≤256B 自动跳过压缩
```

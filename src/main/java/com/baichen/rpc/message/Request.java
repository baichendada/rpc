package com.baichen.rpc.message;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * RPC 请求消息
 * 客户端发起远程调用时封装的信息
 */
@Data
public class Request {

    private static final AtomicInteger ADDER = new AtomicInteger();

    /**
     * 请求 ID，用于唯一标识一次 RPC 调用，服务端响应时会携带这个 ID 以便客户端匹配响应结果
     */
    private Integer requestId = ADDER.getAndIncrement();

    /**
     * 服务名称，用于标识需要调用哪个服务
     */
    private String serviceName;

    /**
     * 方法名称，需要调用的具体方法名
     */
    private String methodName;

    /**
     * 参数类型列表，用于方法反射调用
     */
    private Class<?>[] paramsClass;

    /**
     * 参数值列表，方法调用的实际参数
     */
    private Object[] params;
}

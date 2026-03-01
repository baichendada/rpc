package com.baichen.rpc.message;

import lombok.Data;

/**
 * RPC 请求消息
 * 客户端发起远程调用时封装的信息
 */
@Data
public class Request {
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

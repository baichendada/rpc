package com.baichen.rpc.message;

import lombok.Data;

/**
 * RPC 响应消息
 * 服务端返回给客户端的执行结果
 */
@Data
public class Response {

    /**
     * 方法调用的返回值
     */
    Object result;
}

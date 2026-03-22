package com.baichen.rpc.message;

import lombok.Data;
import lombok.Getter;

import java.io.Serializable;

/**
 * RPC 响应消息
 * 服务端返回给客户端的执行结果
 */
@Data
public class Response implements Serializable {

    private Integer requestId;

    /**
     * 方法调用的返回值
     */
    private Object result;

    private int code;

    private String errorMessage;

    public static Response success(Object result, Integer requestId) {
        Response response = new Response();
        response.setResult(result);
        response.setCode(ResponseCode.SUCCESS.getCode());
        response.setRequestId(requestId);
        return response;
    }

    public static Response fail(String errorMessage, Integer requestId) {
        Response response = new Response();
        response.setCode(ResponseCode.ERROR.getCode());
        response.setErrorMessage(errorMessage);
        response.setRequestId(requestId);
        return response;
    }

    @Getter
    public enum ResponseCode {
        SUCCESS(200),
        ERROR(400),
        ;

        private final int code;

        ResponseCode(int code) {
            this.code = code;
        }
    }
}

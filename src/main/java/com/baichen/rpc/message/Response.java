package com.baichen.rpc.message;

import lombok.Data;
import lombok.Getter;

/**
 * RPC 响应消息
 * 服务端返回给客户端的执行结果
 */
@Data
public class Response {

    /**
     * 方法调用的返回值
     */
    private Object result;

    private int code;

    private String errorMessage;

    public static Response success(Object result) {
        Response response = new Response();
        response.setResult(result);
        response.setCode(ResponseCode.SUCCESS.getCode());
        return response;
    }

    public static Response fail(String errorMessage) {
        Response response = new Response();
        response.setCode(ResponseCode.ERROR.getCode());
        response.setErrorMessage(errorMessage);
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

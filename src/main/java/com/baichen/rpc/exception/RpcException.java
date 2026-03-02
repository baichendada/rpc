package com.baichen.rpc.exception;

public class RpcException extends RuntimeException{
    public RpcException() {
        super();
    }

    public RpcException(String message) {
        super(message);
    }
}

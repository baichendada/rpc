package com.baichen.rpc.exception;

/**
 * RPC 异常基类
 * <p>
 * 支持通过 {@link #retry()} 方法控制是否允许重试。
 * </p>
 */
public class RpcException extends RuntimeException {
    private final boolean retry;

    public RpcException() {
        this(null, true);
    }

    public RpcException(String message) {
        this(message, true);
    }

    /**
     * 构造函数
     * @param message 异常消息
     * @param retry 是否允许重试
     */
    protected RpcException(String message, boolean retry) {
        super(message);
        this.retry = retry;
    }

    /**
     * 是否允许重试
     * @return true 表示可以重试，false 表示不应该重试
     */
    public boolean retry() {
        return retry;
    }
}

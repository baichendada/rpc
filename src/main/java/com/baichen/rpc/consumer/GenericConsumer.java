package com.baichen.rpc.consumer;

public interface GenericConsumer {

    Object $invoke(String serviceName, String methodName, String[] paramTypes, Object[] args);
}

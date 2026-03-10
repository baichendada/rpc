package com.baichen.rpc.register;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class RedisServiceRegister implements ServiceRegister {
    @Override
    public void init(ServiceRegisterConfig config) throws Exception {
        log.error("redis register not implemented yet");
    }

    @Override
    public void register(ServiceMateData service) throws Exception {
        throw new UnsupportedOperationException("redis register not implemented yet");
    }

    @Override
    public List<ServiceMateData> fetchSeviceList(String serviceName) throws Exception {
        throw new UnsupportedOperationException("redis register not implemented yet");
    }
}

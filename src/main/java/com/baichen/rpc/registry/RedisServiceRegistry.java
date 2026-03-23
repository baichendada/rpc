package com.baichen.rpc.registry;

import com.baichen.rpc.spi.SpiTag;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@SpiTag("redis")
public class RedisServiceRegistry implements ServiceRegistry {
    @Override
    public void init(ServiceRegistryConfig config) throws Exception {
        log.error("redis registry not implemented yet");
    }

    @Override
    public void registry(ServiceMateData service) throws Exception {
        throw new UnsupportedOperationException("redis registry not implemented yet");
    }

    @Override
    public List<ServiceMateData> fetchSeviceList(String serviceName) throws Exception {
        throw new UnsupportedOperationException("redis registry not implemented yet");
    }
}

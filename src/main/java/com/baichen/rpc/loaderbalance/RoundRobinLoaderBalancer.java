package com.baichen.rpc.loaderbalance;

import com.baichen.rpc.registry.ServiceMateData;
import com.baichen.rpc.spi.SpiTag;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@SpiTag("roundRobin")
public class RoundRobinLoaderBalancer implements LoaderBalancer {

    private final AtomicInteger count = new AtomicInteger(0);

    @Override
    public ServiceMateData select(List<ServiceMateData> services) {
        return services.get(Math.abs(count.getAndIncrement() % services.size()));
    }
}

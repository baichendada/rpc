package com.baichen.rpc.loaderbalance;

import com.baichen.rpc.registry.ServiceMateData;

import java.util.List;
import java.util.Random;

public class RandomLoaderBalancer implements LoaderBalancer {

    private final Random random = new Random();
    @Override
    public ServiceMateData select(List<ServiceMateData> services) {
        return services.get(random.nextInt(0, services.size()));
    }
}

package com.baichen.rpc.loaderbalance;

import com.baichen.rpc.spi.SpiTag;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

@Slf4j
public class LoaderBalancerManager {

    private final Map<String, LoaderBalancer> balancers = new HashMap<>();

    public LoaderBalancerManager() {
        ServiceLoader<LoaderBalancer> loader = ServiceLoader.load(LoaderBalancer.class);
        for (LoaderBalancer balancer : loader) {
            SpiTag annotation = balancer.getClass().getAnnotation(SpiTag.class);
            if (annotation == null) {
                log.warn("LoaderBalancer {} does not have SpiTag annotation, skipping", balancer.getClass().getName());
                continue;
            }
            String name = annotation.value().toLowerCase();
            if (balancers.put(name, balancer) != null) {
                throw new IllegalArgumentException("Duplicate LoaderBalancer name: " + name);
            }
        }
    }

    public LoaderBalancer getLoaderBalancer(String name) {
        LoaderBalancer balancer = balancers.get(name.toLowerCase());
        if (balancer == null) {
            throw new IllegalArgumentException("Unsupported load balance policy: " + name);
        }
        return balancer;
    }
}

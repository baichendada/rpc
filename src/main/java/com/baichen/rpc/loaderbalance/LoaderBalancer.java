package com.baichen.rpc.loaderbalance;

import com.baichen.rpc.registry.ServiceMateData;

import java.util.List;

public interface LoaderBalancer {

    ServiceMateData select(List<ServiceMateData> services);
}

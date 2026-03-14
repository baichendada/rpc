package com.baichen.rpc.retry;

import com.baichen.rpc.exception.RpcException;
import com.baichen.rpc.loaderbalance.LoaderBalancer;
import com.baichen.rpc.message.Response;
import com.baichen.rpc.registry.ServiceMateData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FailOverPolicy implements RetryPolicy {
    @Override
    public Response retry(RetryContext context) throws Exception {
        List<ServiceMateData> retryList = new ArrayList<>(context.getRetryList());
        retryList.remove(context.getFailedService());
        if (retryList.isEmpty()) {
            throw new RpcException("No more service to retry");
        }

        LoaderBalancer loaderBalancer = context.getLoaderBalancer();
        ServiceMateData retryService = loaderBalancer.select(retryList);
        return context.getRetryFunction().apply(retryService)
                .get(Math.max(context.getWaitResponseTimeoutMillis(), context.getTotalTimeoutMs()), TimeUnit.MILLISECONDS);
    }
}

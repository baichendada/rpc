package com.baichen.rpc.retry;

import com.baichen.rpc.exception.RpcException;
import com.baichen.rpc.message.Response;
import com.baichen.rpc.registry.ServiceMateData;
import com.baichen.rpc.spi.SpiTag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SpiTag("forkAll")
public class ForkAllPolicy implements RetryPolicy {
    @Override
    public Response retry(RetryContext context) {
        List<ServiceMateData> retryList = new ArrayList<>(context.getRetryList());
        retryList.remove(context.getFailedService());
        if (retryList.isEmpty()) {
            throw new RpcException("No more service to retry");
        }

        CompletableFuture[] futures = new CompletableFuture[retryList.size()];
        for (int i = 0; i < retryList.size(); i++) {
            futures[i] = context.getRetryFunction().apply(retryList.get(i));
        }
        CompletableFuture<Object> finishedFuture = CompletableFuture.anyOf(futures);
        try {
            return (Response) finishedFuture.get(Math.min(context.getWaitResponseTimeoutMillis(), context.getTotalTimeoutMs()), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Retry failed for all services", e);
        }
    }
}

package com.baichen.rpc.retry;

import com.baichen.rpc.loaderbalance.LoaderBalancer;
import com.baichen.rpc.message.Response;
import com.baichen.rpc.registry.ServiceMateData;
import lombok.Data;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Data
public class RetryContext {
    private ServiceMateData failedService;
    private List<ServiceMateData> retryList;
    private Long waitResponseTimeoutMillis;
    private Long totalTimeoutMs;
    private LoaderBalancer loaderBalancer;

    private Function<ServiceMateData, CompletableFuture<Response>> retryFunction;
}

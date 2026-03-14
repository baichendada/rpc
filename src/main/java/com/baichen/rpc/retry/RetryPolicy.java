package com.baichen.rpc.retry;

import com.baichen.rpc.message.Response;

public interface RetryPolicy {

    Response retry(RetryContext context) throws Exception;
}

package com.baichen.rpc.retry;

import com.baichen.rpc.message.Response;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RetrySamePolicy implements RetryPolicy {

    private final Random random = new Random();

    @Override
    public Response retry(RetryContext context) {
        int retryCount = 0;
        long endTime = System.currentTimeMillis() + context.getTotalTimeoutMs();
        while (true) {
            try {
                long waitTimeMs = getWaitTimeMs(retryCount);
                if (waitTimeMs >= 1000) {
                    waitTimeMs = 1000;
                }
                Thread.sleep(waitTimeMs);

                long lastTimeoutMs = endTime - System.currentTimeMillis();
                if (lastTimeoutMs < 0) {
                    throw new TimeoutException("Total retry timeout exceeded");
                }
                return context.getRetryFunction()
                        .apply(context.getFailedService())
                        .get(Math.min(context.getWaitResponseTimeoutMillis(), lastTimeoutMs)
                                , TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                retryCount++;
                if (retryCount >= 3) {
                    throw new RuntimeException("Retry failed after 3 attempts", e);
                }
            }
        }
    }


    private long getWaitTimeMs(int retryCount) {
        // Exponential backoff strategy: 100ms, 200ms, 400ms
        return (long) (100 * Math.pow(2, retryCount - 1)) + (long) (random.nextLong(0, 50));
    }
}

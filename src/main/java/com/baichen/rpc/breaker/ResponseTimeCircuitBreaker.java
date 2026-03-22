package com.baichen.rpc.breaker;

import com.baichen.rpc.metrics.MetricsData;
import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Data
public class ResponseTimeCircuitBreaker implements CircuitBreaker {

    // 断路器断路的时间
    private final long breakMs = 5000;

    private final long breakRecordDuration = 10000;

    private final long slotDuration = 1000;

    private final SlotData[] slots = new SlotData[(int)(breakRecordDuration / slotDuration)];

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    private final long slowRequestThresholdMs;

    private final double slowRequestRatioThreshold;

    private final int minRequestCountThreshold = 5;

    private final Lock lock = new ReentrantLock();

    private volatile long lastBreakTime = 0;

    private volatile int currentIndex = 0;

    private volatile long currentIndexStartTime = System.currentTimeMillis() / slotDuration * slotDuration;

    public ResponseTimeCircuitBreaker(long slowRequestThresholdMs, double slowRequestRatioThreshold) {
        this.slowRequestRatioThreshold = slowRequestRatioThreshold;
        this.slowRequestThresholdMs = slowRequestThresholdMs;
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new SlotData();
        }
    }

    @Override
    public boolean allowRequest() {
        if (State.HALF_OPEN.equals(state.get())) {
            return false;
        } else if (State.CLOSED.equals(state.get())) {
            return true;
        }
        long now = System.currentTimeMillis();
        return now - lastBreakTime > breakMs && state.compareAndSet(State.OPEN, State.HALF_OPEN);
    }

    @Override
    public void recordRpc(MetricsData metricsData) {
        long now = System.currentTimeMillis();
        slideWindowIfNecessary(now);
        boolean isSlowRequest = !metricsData.isSuccess() || metricsData.getDuration() >= slowRequestThresholdMs;
        switch (state.get()) {
            case CLOSED -> processClosedState(isSlowRequest);
            case HALF_OPEN -> processHalfOpenState(isSlowRequest);
            case OPEN -> processOpenState(isSlowRequest);
        }
    }

    private void slideWindowIfNecessary(long now) {
        if (now - currentIndexStartTime < slotDuration) {
            return;
        }

        try {
            lock.lock();
            int diff = (int) ((now - currentIndexStartTime) / slotDuration);
            int step = Math.min(diff, slots.length);
            for (int i = 0; i < step; i++) {
                int index = (currentIndex + i + 1) % slots.length;
                slots[index].getRequestCount().set(0);
                slots[index].getFailedCount().set(0);
            }
            currentIndex = (currentIndex + step) % slots.length;
            currentIndexStartTime = now / slotDuration * slotDuration;
        } finally {
            lock.unlock();
        }
    }

    private void processClosedState(boolean isSlowRequest) {
        SlotData slotData = slots[currentIndex];
        // 无论成功还是失败，都应该增加请求总数
        slotData.requestCount.incrementAndGet();

        if (!isSlowRequest) {
            return;
        }

        slotData.failedCount.incrementAndGet();
        int totalRequestCount = 0, totalFailedCount = 0;
        for (int i = 0; i < slots.length; i++) {
            totalRequestCount += slots[i].getRequestCount().get();
            totalFailedCount += slots[i].getFailedCount().get();
        }

        if (totalRequestCount < minRequestCountThreshold) {
            return;
        }

        double failedRatio = ((double) totalFailedCount) / totalRequestCount;
        if (failedRatio > slowRequestRatioThreshold && state.compareAndSet(State.CLOSED, State.OPEN)) {
            lastBreakTime = System.currentTimeMillis();
        }
    }

    private void processHalfOpenState(boolean isSlowRequest) {
        if (isSlowRequest && state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            lastBreakTime = System.currentTimeMillis();
        } else {
            state.compareAndSet(State.HALF_OPEN, State.CLOSED);
        }
    }

    private void processOpenState(boolean isSlowRequest) {
    }


    @Data
    private static class SlotData {
        private AtomicInteger requestCount = new AtomicInteger(0);
        private AtomicInteger failedCount = new AtomicInteger(0);
    }
}

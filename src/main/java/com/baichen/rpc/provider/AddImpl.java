package com.baichen.rpc.provider;

import com.baichen.rpc.api.Add;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class AddImpl implements Add {
    @Override
    public int add(int a, int b) {
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(4));
        return a + b;
    }

    @Override
    public int minus(int a, int b) {
        return a - b;
    }
}

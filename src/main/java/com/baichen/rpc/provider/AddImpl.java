package com.baichen.rpc.provider;

import com.baichen.rpc.api.Add;

public class AddImpl implements Add {
    @Override
    public int add(int a, int b) {
        return a + b;
    }

    @Override
    public int minus(int a, int b) {
        return a - b;
    }
}

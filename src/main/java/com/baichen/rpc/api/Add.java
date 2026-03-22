package com.baichen.rpc.api;

import com.baichen.rpc.fallback.FallbackTag;

@FallbackTag(AddFallbackImpl.class)
public interface Add {
    int add(int a, int b);
    int minus(int a, int b);
}

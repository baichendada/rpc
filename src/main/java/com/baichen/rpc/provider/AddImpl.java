package com.baichen.rpc.provider;

import com.baichen.rpc.api.Add;
import com.baichen.rpc.api.User;

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

    @Override
    public User merge(User user1, User user2) {
        User user = new User();
        user.setAge(user1.getAge() + user2.getAge());
        user.setName(user1.getName() + user2.getName());
        return user;
    }
}

package com.zazhi.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author zazhi
 * @date 2025/2/16
 * @description: TODO
 */
public class SimpleRedisLock implements ILock {

    String name; // 业务名称
    StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX="lock:";

    @Override
    public boolean lock(long timeoutSec) {
        String threadName = Thread.currentThread().getName();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadName, timeoutSec, TimeUnit.SECONDS);
        // 直接返回 success 有可能会拆箱时空指针异常
        return Boolean.TRUE.equals(success);
    }

    @Override
    public boolean unlock() {
        Boolean success = stringRedisTemplate.delete(KEY_PREFIX + name);
        return Boolean.TRUE.equals(success);
    }
}

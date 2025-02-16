package com.zazhi.utils;

import cn.hutool.core.lang.UUID;
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
    private static final String ID_PREFIX= UUID.randomUUID().toString(true) + "-";


    @Override
    public boolean lock(long timeoutSec) {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        // 直接返回 success 有可能会拆箱时空指针异常
        return Boolean.TRUE.equals(success);
    }

    @Override
    public boolean unlock() {
        // 获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁中的标示
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        if(threadId.equals(id)){
            Boolean success = stringRedisTemplate.delete(KEY_PREFIX + name);
            return Boolean.TRUE.equals(success);
        }
        return false;
    }
}

package com.zazhi.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;
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

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

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
//        // 获取线程标示
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        // 获取锁中的标示
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//
//        if(threadId.equals(id)){
//            Boolean success = stringRedisTemplate.delete(KEY_PREFIX + name);
//            return Boolean.TRUE.equals(success);
//        }
//        return false;

        // 使用lua脚本保证原子性

        List<String> keys = new ArrayList<>();
        keys.add(KEY_PREFIX + name);

        Long res = stringRedisTemplate.execute(UNLOCK_SCRIPT, keys, ID_PREFIX + Thread.currentThread().getId());
        return res != null && res > 0;
    }
}

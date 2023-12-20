package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 使用 Redis 实现分布式锁初级版本
 * @author Ghost
 * @version 1.0
 */
public class SimpleRedisLock implements ILock{

    private String name;// 业务名称
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";

    @Override
    public boolean tryLock(Long timeoutSec) {
        // 获取当前线程名称当作 value
        long threadId = Thread.currentThread().getId();
        stringRedisTemplate.opsForValue().set("test", "test");
        // 获取锁
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unLock() {
        // 释放锁
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}

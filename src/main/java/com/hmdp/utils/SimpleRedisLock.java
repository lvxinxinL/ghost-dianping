package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
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
    private static final String ID_PREFIX = UUID.fastUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    /**
     * 初始化 Lua 脚本
     */
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        // 改进：防止误删锁，需要增加判断当前锁是不是自己的锁的逻辑=>通过判断线程标识
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unLock() {
        // 调用 Lua 脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX +Thread.currentThread().getId());
    }

    /*@Override
    public void unLock() {
        // 获取当前线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 判断标识是否与锁的线程标识一致（当前线程和当前锁的占有者的线程）
        String lockHolder = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (threadId.equals(lockHolder)) {
            // 释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }*/
}

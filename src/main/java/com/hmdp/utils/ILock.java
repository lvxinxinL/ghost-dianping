package com.hmdp.utils;

/**
 * 使用 Redis 实现分布式锁业务接口
 * @author Ghost
 * @version 1.0
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 超时时间
     * @return true 代表获取锁成功，false 代表获取锁失败
     */
    boolean tryLock(Long timeoutSec);

    /**
     * 释放锁
     */
    void unLock();
}

package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * 缓存工具类
 * @author Ghost
 * @version 1.0
 */
@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;


    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将 Java 对象存入 Redis 并设置 TTL
     * @param key 键
     * @param value 值
     * @param time 有效时长
     * @param unit 时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将 Java 对象存入 Redis 并设置 逻辑过期时间
     * @param key 键
     * @param value 值
     * @param time 有效时长
     * @param unit 时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 添加逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入 Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 通过缓存空值解决缓存穿透
     * @param keyPrefix Redis 中数据 key 的前缀
     * @param id 数据库中的 id
     * @param type 查询结果的类型
     * @param dbFallback 查询数据库的函数
     * @param time 过期时间
     * @param unit 时间单位
     * @return 查询到的数据
     * @param <R> 返回数据的类型
     * @param <ID> 查询数据库的字段名
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 查询 Redis
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 存在直接返回
        if (StrUtil.isNotBlank(json)) {
            // 反序列化成 Java 对象
            return JSONUtil.toBean(json, type);
        }

        // 判断是否命中空字符串
        if(json != null) {
            return null;
        }

        // 3. 不存在查询数据库
        R r = dbFallback.apply(id);

        // 4. 数据库不存在
        if(r == null) {
            // 数据库中不存在该数据，将空值写入 Redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);// 序列化为字符串存入 Redis
            // 返回错误信息
            return null;
        }

        // 5. 存在，写入 Redis，设置过期时间
        this.set(key, r, time, unit);

        // 6. 返回商铺查询结果
        return r;
    }

    // 创建线程池，开启独立线程
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期解决缓存穿透
     * @param id 商铺id
     * @return 商铺信息
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 查询 Redis
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 未命中，直接返回空
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 3. 命中，将 JSON 字符串转为 Java 对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);// 获取商铺信息
        LocalDateTime expireTime = redisData.getExpireTime();// 获取逻辑过期时间

        // 4. 判断是否逻辑过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 4.1 未过期，返回商铺信息
            return r;
        }
        // 4.2 过期，重建缓存
        // 5. 重建缓存
        // 5.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 5.3 获取互斥锁成功，再次判断是否过期
        if(isLock) {
            json = stringRedisTemplate.opsForValue().get(key);
            // 命中，将 JSON 字符串转为 Java 对象
            redisData = JSONUtil.toBean(json, RedisData.class);
            r = JSONUtil.toBean((JSONObject) redisData.getData(), type);// 获取商铺信息
            expireTime = redisData.getExpireTime();// 获取逻辑过期时间
            // 5.4 未过期就不用重建，直接返回
            if(expireTime.isAfter(LocalDateTime.now())){
                return r;
            }
            // 5.5 已过期，开启独立线程，进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    // 写入 Redis
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放互斥锁
                    unLock(lockKey);
                }
            });
        }
        // 6. 返回过期数据
        return r;
    }

    /**
     * 尝试获取互斥锁
     * @param key 互斥锁的键名
     * @return 成功：true 失败：false
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key 互斥锁的键名
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}

package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * 商铺相关业务逻辑
 * @author Ghost
 * @version 1.0
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据 id 查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    public Result queryById(Long id) {
        // 缓存空值解决缓存穿透
//        Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("商铺信息不存在！");
        }

        // 6. 返回商铺查询结果
        return Result.ok(shop);
    }

    /**
     * 互斥锁解决缓存击穿问题
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 查询 Redis
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 存在直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            // 反序列化成 Java 对象
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断是否命中空字符串(!= null 就是空串 "")
        if(shopJson != null) {
            return null;
        }

        // 4. 实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2 判断是否获取成功
            if(!isLock) {
                // 4.3 获取锁失败，休眠一段时间再重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 4.4 获取锁成功，根据 id 查询数据库，重新写入 Redis
            // 获取锁成功之后应该再次检测 Redis 缓存是否存在，如果存在则无需重建
            shopJson = stringRedisTemplate.opsForValue().get(key);// Double Check
            if (StrUtil.isNotBlank(shopJson)) {
                // 反序列化成 Java 对象
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            // 缓存里还是没有商铺数据，继续查询数据库
            shop = getById(id);
            // 模拟重建缓存的延迟
            Thread.sleep(200);
            // 数据库不存在
            if(shop == null) {
                // 数据库中不存在该数据，将空值写入 Redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);// 序列化为字符串存入 Redis
                // 返回错误信息
                return null;
            }
            // 5. 存在，写入 Redis，设置过期时间
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);// 序列化为字符串存入 Redis
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unLock(lockKey);
        }
        // 6. 返回商铺查询结果
        return shop;
    }

    /**
     * 通过缓存空值解决缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 查询 Redis
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 存在直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            // 反序列化成 Java 对象
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断是否命中空字符串
        if(shopJson.equals("")) {
            return null;
        }

        // 3. 不存在查询数据库
        Shop shop = getById(id);

        // 4. 数据库不存在
        if(shop == null) {
            // 数据库中不存在该数据，将空值写入 Redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);// 序列化为字符串存入 Redis
            // 返回错误信息
            return null;
        }

        // 5. 存在，写入 Redis，设置过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);// 序列化为字符串存入 Redis

        // 6. 返回商铺查询结果
        return shop;
    }

    /**
     * 尝试获取互斥锁
     * @param key 互斥锁的键名
     * @return 是否成功获取到互斥锁
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
    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null) {
            return Result.fail("商铺 id 不能为空");
        }
        // 1. 更新数据库
        updateById(shop);

        // 2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}

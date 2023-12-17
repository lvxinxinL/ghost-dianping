package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_KEY;

/**
 * 商铺类型相关业务
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询店铺类型
     * @return 店铺分类信息
     */
    public Result queryTypeList() {
        // 1. 查询 Redis
//        List<String> shopTypeStrList = stringRedisTemplate.opsForList().range(SHOP_TYPE_KEY, 0, -1);
        String typeStr = stringRedisTemplate.opsForValue().get(SHOP_TYPE_KEY);
        List<ShopType> shopTypeList = JSONUtil.toList(typeStr, ShopType.class);

        // 2. 存在，直接返回
        if(!shopTypeList.isEmpty()) {
            return Result.ok(shopTypeList);
        }

        // 3. 不存在，查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 4. 数据库不存在数据，直接返回
        if(typeList == null) {
            return Result.fail("商铺类型不存在！");
        }

        // 5. 数据库存在，写入 Redis
        String typeJson = JSONUtil.toJsonStr(typeList);
        stringRedisTemplate.opsForValue().set(SHOP_TYPE_KEY, typeJson);

        // 6. 返回查询结果
        return Result.ok(typeList);
    }
}

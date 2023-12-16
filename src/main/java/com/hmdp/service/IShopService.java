package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 商铺相关业务逻辑
 * @author Ghost
 * @version 1.0
 */
public interface IShopService extends IService<Shop> {


    /**
     * 根据 id 查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    Result queryById(Long id);


    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    Result update(Shop shop);
}

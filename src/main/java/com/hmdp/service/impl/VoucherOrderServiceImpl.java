package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 优惠券下单业务实现类
 *
 * @author Ghost
 * @version 1.0
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIDWorker redisIDWorker;

    /**
     * 抢购秒杀优惠券
     *
     * @param voucherId
     * @return
     */
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2. 判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始！");
        }

        // 3. 判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束！");
        }

        // 4. 判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足！");
        }

        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {// 锁的是当前用户
            // 获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5. 一人一单
        // 5.1 用户 id
        Long userId = UserHolder.getUser().getId();
            // 5.2 查询订单是否存在
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("每个用户只能购买一次！");
            }

            // 6. 库存充足扣减库存
            boolean success = seckillVoucherService.update().
                    setSql("stock = stock - 1").// set stock = stock - 1
                            eq("voucher_id", voucherId).gt("stock", 0).// where voucher_id = ? and stock > 0
                            update();
            if (!success) {// 更新库存失败
                return Result.fail("库存不足！");
            }

            // 7. 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1 设置全局唯一订单 id
            long orderId = redisIDWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2 设置用户 id
            voucherOrder.setUserId(userId);
            // 7.3 设置优惠券 id
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);// 保存订单

            // 8. 返回订单 id
            return Result.ok(orderId);
    }
}

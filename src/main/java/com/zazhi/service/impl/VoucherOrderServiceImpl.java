package com.zazhi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zazhi.dto.Result;
import com.zazhi.entity.SeckillVoucher;
import com.zazhi.entity.VoucherOrder;
import com.zazhi.mapper.VoucherOrderMapper;
import com.zazhi.service.ISeckillVoucherService;
import com.zazhi.service.IVoucherOrderService;
import com.zazhi.utils.ILock;
import com.zazhi.utils.RedisIdWorker;
import com.zazhi.utils.SimpleRedisLock;
import com.zazhi.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀代金券
     *
     * @param voucherId 代金券ID
     * @return
     */
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 2. 判断是否在活动期间
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("活动未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("活动已结束");
        }
        // 3. 判断库存是否充足
        if (seckillVoucher.getStock() <= 0) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()) {
        ILock lock = new SimpleRedisLock("seckill_order:" + userId, stringRedisTemplate);
        boolean isLock = lock.lock(5);
        if (!isLock) { // 同一个用户的请求，只有一个能拿到锁
            return Result.fail("请勿重复秒杀");
        }

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放锁
            lock.unlock();
        }
//        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 4. 一人只能秒杀一张
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("voucher_id", voucherId)
                .eq("user_id", userId)
                .count();
        if (count > 0) {
            return Result.fail("您已经秒杀过了");
        }

        // 5. 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
//                .eq("stock", seckillVoucher.getStock()) // 乐观锁 CAS
                .gt("stock", 0) // 乐观锁改进
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }


        // 6. 生成订单
        VoucherOrder order = new VoucherOrder();
        order.setId(redisIdWorker.nextId("order")); // 生成订单ID
        order.setVoucherId(voucherId); // 代金券ID
        order.setUserId(userId); // 用户ID
        save(order);
        // 7. 返回订单ID
        return Result.ok(order.getId());
    }
}

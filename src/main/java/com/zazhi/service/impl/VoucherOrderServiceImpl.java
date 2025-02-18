package com.zazhi.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zazhi.dto.Result;
import com.zazhi.entity.VoucherOrder;
import com.zazhi.mapper.VoucherOrderMapper;
import com.zazhi.service.ISeckillVoucherService;
import com.zazhi.service.IVoucherOrderService;
import com.zazhi.utils.RedisIdWorker;
import com.zazhi.utils.UserHolder;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    //异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {

        // 5. 扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherOrder.getVoucherId())
//                .eq("stock", seckillVoucher.getStock()) // 乐观锁 CAS
                .gt("stock", 0) // 乐观锁改进
                .update();

        // 6. 生成订单
        VoucherOrder order = new VoucherOrder();
        order.setId(redisIdWorker.nextId("order")); // 生成订单ID
        order.setVoucherId(voucherOrder.getVoucherId()); // 代金券ID
        order.setUserId(voucherOrder.getUserId()); // 用户ID
        save(order);
    }

    // 用于线程池处理的任务
    // 当初始化完毕后，就会去从对列中去拿信息
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
//            while (true){
//                try {
//                    // 1.获取队列中的订单信息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    // 2.创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//            }

            while (true) {
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"), StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)), StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    //处理异常消息
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"), StreamReadOptions.empty().count(1), StreamOffset.create("stream.orders", ReadOffset.from("0")));
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有异常消息，结束循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理pendding订单异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (Exception ex) {
                        e.printStackTrace();
                    }
                }
            }
        }

//        private void handleVoucherOrder(VoucherOrder voucherOrder) {
//            //1.获取用户
//            Long userId = voucherOrder.getUserId();
//            // 2.创建锁对象
//            RLock redisLock = redissonClient.getLock("lock:order:" + userId);
//            // 3.尝试获取锁
//            boolean isLock = redisLock.tryLock();
//            // 4.判断是否获得锁成功
//            if (!isLock) {
//                // 获取锁失败，直接返回失败或者重试
//                log.error("不允许重复下单！");
//                return;
//            }
//            try {
//                //注意：由于是spring的事务是放在threadLocal中，此时的是多线程，事务会失效
//                proxy.createVoucherOrder(voucherOrder);
//            } finally {
//                // 释放锁
//                redisLock.unlock();
//            }
//        }
    }

    private IVoucherOrderService proxy;

    /**
     * 秒杀代金券
     *
     * @param voucherId 代金券ID
     * @return
     */
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString(), String.valueOf(orderId));
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
//        // 3.生成订单保存阻塞队列
//        VoucherOrder order = new VoucherOrder();
//        order.setId(redisIdWorker.nextId("order")); // 生成订单ID
//        order.setVoucherId(voucherId); // 代金券ID
//        order.setUserId(userId); // 用户ID
//        orderTasks.add(order);

        //3.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 4.返回订单id
        return Result.ok(orderId);
    }


//    /**
//     * 秒杀代金券
//     *
//     * @param voucherId 代金券ID
//     * @return
//     */
//    @Transactional
//    public Result seckillVoucher(Long voucherId) {
//        // 1. 查询优惠券
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        // 2. 判断是否在活动期间
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("活动未开始");
//        }
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("活动已结束");
//        }
//        // 3. 判断库存是否充足
//        if (seckillVoucher.getStock() <= 0) {
//            return Result.fail("库存不足");
//        }
//
//        Long userId = UserHolder.getUser().getId();
////        synchronized (userId.toString().intern()) {
//
////        ILock lock = new SimpleRedisLock("seckill_order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:seckill_order:" + userId);
//
////         boolean isLock = lock.lock(10);
//        boolean isLock = lock.tryLock();
//
//        if (!isLock) { // 同一个用户的请求，只有一个能拿到锁
//            return Result.fail("请勿重复秒杀");
//        }
//
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            // 释放锁
//            lock.unlock();
//        }
////        }
//    }
//
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        // 4. 一人只能秒杀一张
//        Long userId = UserHolder.getUser().getId();
//        int count = query().eq("voucher_id", voucherId)
//                .eq("user_id", userId)
//                .count();
//        if (count > 0) {
//            return Result.fail("您已经秒杀过了");
//        }
//
//        // 5. 扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
////                .eq("stock", seckillVoucher.getStock()) // 乐观锁 CAS
//                .gt("stock", 0) // 乐观锁改进
//                .update();
//        if (!success) {
//            return Result.fail("库存不足");
//        }
//
//
//        // 6. 生成订单
//        VoucherOrder order = new VoucherOrder();
//        order.setId(redisIdWorker.nextId("order")); // 生成订单ID
//        order.setVoucherId(voucherId); // 代金券ID
//        order.setUserId(userId); // 用户ID
//        save(order);
//        // 7. 返回订单ID
//        return Result.ok(order.getId());
//    }
}

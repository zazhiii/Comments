package com.zazhi.service;

import com.zazhi.dto.Result;
import com.zazhi.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀代金券
     * @param voucherId 代金券ID
     * @return
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 创建代金券订单
     * @param voucherId 代金券ID
     * @return
     */
    Result createVoucherOrder(Long voucherId);
}

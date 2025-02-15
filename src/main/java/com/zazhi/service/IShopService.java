package com.zazhi.service;

import com.zazhi.dto.Result;
import com.zazhi.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    Result queryShopById(Long id);

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     */
    Result update(Shop shop);
}

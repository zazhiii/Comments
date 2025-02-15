package com.zazhi.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.zazhi.dto.Result;
import com.zazhi.entity.Shop;
import com.zazhi.mapper.ShopMapper;
import com.zazhi.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import static com.zazhi.utils.RedisConstants.CACHE_SHOP_KEY;

@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public Result queryShopById(Long id) {
        // 1. 从 Redis 中查询缓存, 判断是否存在
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // 2. 如果缓存中没有数据，则从数据库中查询, 判断是否存在
        Shop shop = this.getById(id);
        if(shop == null) {
            return Result.fail("商铺不存在");
        }
        // 3. 将查询到的数据写入缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
        // 4. 返回查询到的数据
        return Result.ok(shop);
    }
}

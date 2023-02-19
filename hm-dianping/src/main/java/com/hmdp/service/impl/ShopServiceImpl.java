package com.hmdp.service.impl;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 方法一 ： 缓存穿透解决缓存击穿问题
        System.out.println("1---------------");
        //Result shop = queryWithPassThrough(id);
        //方法二：  基于互斥锁方式解决缓存击穿问题
        Result shop = queryWithMutex(id);
        //方法三 ：逻辑过期设置缓存击穿问题
        //Result shop = queryWithLogicalExpire(id);
        // 3. 返回商户信息
        System.out.println("2---------------");
        return Result.ok(shop);
    }

    //todo 方法二：  基于互斥锁方式解决缓存击穿问题
    public Result queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        // 1. 从redis中查询商铺的缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //  2. 判断redis中是否存在该id的商户
        if(StrUtil.isNotBlank(shopJson)){
            //3. 如果存在： 返回商户的信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        if(shopJson != null){
            return null;
        }
        Shop shopN = null;
        //未命中---------尝试获取互斥锁--------
        String lockKey = LOCK_SHOP_KEY + id;
        //1. 判断获取互斥锁是否成功
        boolean isLock = tryLock(lockKey);
        //2.失败休眠，成功就获取
        if (!isLock){
            queryWithMutex(id);     //递归重试获取互斥锁
        }
        //！获取互斥锁成功
        shopN = getById(id);
        if(shopN == null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        // 4.2数据库中 商户如果存在就将商户信息写入redis ,超时删除30min
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopN),30, TimeUnit.MINUTES);
        unLock(lockKey);
        //6. 返回商户信息
        return Result.ok(shopN);
    }
    //todo 获取锁
    boolean tryLock(String key){
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(aBoolean);
    }
    //todo 释放锁
    void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    //todo 方法一 ： 缓存穿透解决缓存击穿问题
    public Result queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        System.out.println(key);
        System.out.println("1---------------");
        // 1. 从redis中查询商铺的缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //  2. 判断redis中是否存在该id的商户
        System.out.println(shopJson);
        if(StrUtil.isNotBlank(shopJson)){
            //3. 如果存在： 返回商户的信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            System.out.println(shop);
            return Result.ok(shop);
        }
        if(shopJson != null){
            return null;
        }
        Shop shopN = getById(id);
        // 4. 如果不存在:
        if(shopN == null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        // 4.2数据库中 商户如果存在就将商户信息写入redis ,超时删除30min
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopN),30, TimeUnit.MINUTES);
        //   6. 返回商户信息
        return Result.ok(shopN);
    }

    //todo 更新数据库删除缓存
    @Override
    @Transactional //添加事务
    public Result update(Shop shop) {
        //1. 更新数据库
        updateById(shop);
        if(shop.getId() == null){
            return Result.fail("店铺id不能为空！！！");
        }
        Long id = shop.getId();
        String key = CACHE_SHOP_KEY + id;
        //删除缓存
        stringRedisTemplate.delete(key);
        return Result.ok();
    }
}

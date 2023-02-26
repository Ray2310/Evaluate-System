package com.Evaluate.service.impl;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.Evaluate.MyUtils.CacheClient;
import com.Evaluate.dto.Result;
import com.Evaluate.entity.Shop;
import com.Evaluate.mapper.ShopMapper;
import com.Evaluate.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Evaluate.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.Evaluate.utils.RedisConstants.*;
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 方法一 ： 缓存穿透解决缓存击穿问题
        //Result shop = queryWithPassThrough(id);
        //方法二：  基于互斥锁方式解决缓存击穿问题
        //Result shop = queryWithMutex(id);
        //方法三 ：逻辑过期设置缓存击穿问题
        //Result shop = queryWithLogicalExpire(id);
        // 3. 返回商户信息

        //方法四 ：使用自定义的工具类 解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

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


    /**★★★★★
     * 实现了根据地理位置查询商户
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1. 判断是否需要根据坐标查询
        if(x == null || y == null){
            Page<Shop> page = query().eq("type_id",typeId).page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        //2. 计算分页参数
        int from =  (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;


        //3. 查询redis，按照距离排序，分页
        String key = "shop:geo:"  +typeId;
        // GEOSEARCH  key BYLONLAT x y BYRADIUS 10 WITHDISTANCH
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,                        //
                GeoReference.fromCoordinate(x, y),   //中心点
                new Distance(5000),    //指定的附近距离(米)
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        if(results == null){
            return null;
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();

        if(content.size() <= from){
            //也就是说没有下一页,直接结束
            return Result.ok(Collections.emptyList());
        }
        //截取从from到end的部分
        ArrayList<Long> ids = new ArrayList<>(content.size());
        Map<String,Distance> distanceMap = new HashMap<>(content.size());

        content.stream().skip(from).forEach(result->{
            String shopId = result.getContent().getName();
            Distance distance = result.getDistance(); //获取距离
            distanceMap.put(shopId,distance);
        });

        //5. 根据店铺id查询店铺
        String idStr = StrUtil.join(",",ids);
        List<Shop> shops = query().eq("id",ids).last("ORDER BY FIELD(id ," + idStr + ")").list();

        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);

    }
}

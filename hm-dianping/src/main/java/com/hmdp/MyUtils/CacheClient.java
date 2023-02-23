package com.hmdp.MyUtils;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.utils.RedisData;
import com.sun.org.apache.regexp.internal.RE;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value, Long time , TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time , TimeUnit unit){
        //设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 封装缓存穿透解决缓存击穿问题
     * @param keyPre key的实际前缀
     * @param id 需要查询的XXX的id
     * @param type 查询的信息的类型
     * @param dbFallback 函数式编程的方法
     * @param time 缓存时间
     * @param unit 时间单位(TimeUnit.MINUTES)
     * @param <R> 返回值类型
     * @param <ID> id的类型
     * @return 返回查询到的信息
     */
    public <R,ID> R queryWithPassThrough(
            String keyPre, ID id , Class<R> type , Function<ID , R> dbFallback,
            Long time , TimeUnit unit){
        String key = keyPre + id;
        // 1. 从redis中查询商铺的缓存
        String Json = stringRedisTemplate.opsForValue().get(key);
        //  2. 判断redis中是否存在该id的商户
        if(StrUtil.isNotBlank(Json)){
            //3. 如果存在 : 返回信息
            return JSONUtil.toBean(Json, type);
        }
        if(Json != null){
            return null;
        }
        R r = dbFallback.apply(id);
        // 4. 如果不存在:
        if(r == null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        this.set(key,r,time,unit);
        return r;
    }



}

package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    private static final  String KEY_PREFIX = "lock:";
    private static final  String ID_PREFIX = UUID.randomUUID().toString(true)+"=";




    //todo 获取分布式锁
    @Override
    public boolean tryLock(long timeOutSec) {
        //获取线程的表示
        String value = ID_PREFIX +  Thread.currentThread().getId();
        //获取锁
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, value + "", timeOutSec, TimeUnit.MINUTES);
        //注意自动拆箱出现的空指针错误
        return Boolean.TRUE.equals(aBoolean);
    }

    //todo 释放锁
    @Override
    public void unLock() {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        //获取锁中的标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        //判断两种标识是否一致
        if(id.equals(threadId)){
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}

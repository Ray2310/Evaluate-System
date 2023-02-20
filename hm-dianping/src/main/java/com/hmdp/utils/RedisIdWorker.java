package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * id生成器
 * 时间戳
 * - 符号位： 1bit ，永远为0
 * - 时间戳：31bit，以秒为单位，可以使用69年
 * - 序列号：32bit，秒内的计数器，支持每秒产生2^32个不同ID
 */
@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //起始时间戳
    private static final long BEGIN_TIMESTAMP = 1620995200L;
    //设置序列号的位数
    private static final int COUNT_BITS = 32;

    public long nextId(String keyPre){
        //1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSeconds - BEGIN_TIMESTAMP;

        //2. 生成序列号 (将日期精确到天)
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long aLong = stringRedisTemplate.opsForValue().increment("icr:" + keyPre + ":" + date);

        //3. 拼接
        return timestamp << COUNT_BITS | aLong;
    }
}

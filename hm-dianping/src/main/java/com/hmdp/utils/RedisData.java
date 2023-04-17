package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 *数据写入redis中设置逻辑过期时间。
 *
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;//逻辑过期时间
    private Object data;   //附带的写入redis的数据
}

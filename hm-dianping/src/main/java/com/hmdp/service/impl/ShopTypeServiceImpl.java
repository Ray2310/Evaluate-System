package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        //先查看缓存中是否存在
        List<String> range = stringRedisTemplate.opsForList().range("shopTypeList", 0, -1);
        //如果存在，那么就直接返回
        if(!range.isEmpty()){
            return Result.ok(range);
        }
        //如果不存在，先从数据库中查到，然后再交给redis，然后再返回
        List<ShopType> sort = query().orderByAsc("sort").list();
        for(ShopType item : sort){
            stringRedisTemplate.opsForList().rightPush("shopTypeList", JSONUtil.toJsonStr(item));
        }
        List<String> typeList = stringRedisTemplate.opsForList().range("shopTypeList", 0, -1);
        return Result.ok(typeList);
    }
}

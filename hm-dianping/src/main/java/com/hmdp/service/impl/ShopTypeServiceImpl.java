package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        //先查看缓存中是否存在
        String key = "shopType:List";
        List<String> shopTypeList = new ArrayList<>();
        shopTypeList = stringRedisTemplate.opsForList().range(key, 0, -1);
        //如果存在，那么就直接返回
        if(!shopTypeList.isEmpty()){
            List<ShopType> typeList = new ArrayList<>();
            for (String s:shopTypeList) {
                ShopType shopType = JSONUtil.toBean(s, ShopType.class);

                typeList.add(shopType);
            }
            return Result.ok(typeList);
        }
        //如果不存在，先从数据库中查到，然后再交给redis，然后再返回
        List<ShopType> typeList = query().orderByAsc("sort").list();
        for(ShopType item : typeList){
            String s = JSONUtil.toJsonStr(item);
            shopTypeList.add(s);
        }
        stringRedisTemplate.opsForList().rightPushAll(key,shopTypeList);
        return Result.ok(typeList);
    }
}

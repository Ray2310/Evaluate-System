package com.Evaluate;

import com.Evaluate.entity.Shop;
import com.Evaluate.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    @Test
    void insert(){
        List<Shop> list = shopService.list();
        Map<Long,List<Shop>> map  = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        for(Map.Entry<Long,List<Shop>> entry  : map.entrySet()){
            Long typeId = entry.getKey();
            String key = "shop:geo:" +  typeId;
            List<Shop> value = entry.getValue();
            ArrayList<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for(Shop shop : value){
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }

    }
}

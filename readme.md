# 项目简介：
  该项目是一个基于Redis实现的仿大众点评项目,旨在提供一个平台，供用户分享生活中的美好事物。 项目采用了前后端分离技术，支持用户登录注册、商品查询、优惠卷秒杀下单、用户分享优质店铺、好友关注、用户签到等等一系列功能。
  
# 项目技术栈
后端
- SpringBoot
- MyBatis-Plus
- SpringMVC
- MySQL
- Redis
- ......

# 项目亮点
- [x] 通过Redis实现了Session在集群下的共享问题
- [x] 基于List实现了点赞列表， 并且基于SortedSet的点赞排行榜
- [x] 基于Set集合实现了 关注、取关、共同关注、消息推送等功能
- [x] 基于Redis的BitMap实现了 数据统计 从而实现了用户签到
- [x] 通过缓存空对象的方法解决了在查询商户时的缓存穿透问题
- [x] 通过设置添加锁机制 解决了缓存击穿
- ......




前端
- 参考开源项目实现( 黑马点评 )

# 项目部署 和 配置
1. 数据库配置

![image](https://user-images.githubusercontent.com/109897266/232434595-4547b04a-26d3-40f4-9cc3-862975efc921.png)

2. redis配置

![image](https://user-images.githubusercontent.com/109897266/232434656-48ca8c8a-d02f-4a64-bbf6-f7622a51f206.png)


3. 项目启动

![image](https://user-images.githubusercontent.com/109897266/232434730-a523cbbe-59d4-4570-8369-7b0f5a5fe51b.png)


# 项目部分实现展示
1. 项目首页

![image](https://user-images.githubusercontent.com/109897266/232435700-1083c027-f195-4562-b8ab-6a0b03b17fa7.png)

2. 登录页

![image](https://user-images.githubusercontent.com/109897266/232435900-745078b7-85e7-4e8b-9f17-28b82ad2aeae.png)


3. 达人探店

![image](https://user-images.githubusercontent.com/109897266/232435951-6254187d-bcc2-48db-9cdd-1dad001adc88.png)


4. 发布文章

![image](https://user-images.githubusercontent.com/109897266/232436003-51ebddda-faaf-4009-9aa9-47982a400f8d.png)


5. 查看所有店铺

![image](https://user-images.githubusercontent.com/109897266/232436045-030c8db1-f667-4411-8bc3-bb378b880128.png)   


# 后续可优化部分

- [ ] SpringSecurity实现登录优化
- [ ] 使用布隆过滤解决缓存穿透问题，它的优点: 内存占用较少，没有多余key
- [ ] 可以试着自己封装缓存工具
- [ ] 对于缓存雪崩，以后试着用利用Redis集群提高服务的可用性、 给缓存业务添加降级限流策略 、 给业务添加多级缓存，而不是如今简单的实现给不同的Key的TTL添加随机值
- [ ] 点赞功能还可实现缓存更新的策略，也就是说使用redis实现点赞功能的同时设置缓存时效，当时间到了之后就将数据写入数据库，同时删除缓存`(既减少了对数据库的访问又降低了缓存的压力)`
- ......


# 项目介绍
## 项目架构
**后端框架 :** springBoot 、SpringMVC 、Mybatis-Plus 、 MySQL 、Redis


## 已经实现功能介绍
### 短信登录
基于redis实现了验证码及其密码的时间段内存储。解决session共享
> 当用户第一次进入系统时，**tomcat服务器①**接收到请求，然后进行处理用户的请求（登录注册等）。
>当用户第二次进入系统时 ，被负载均衡到了**tomca服务器②** 。用户的信息其实是已经注册了的，但是这里却无法获取。导致用户还得注册...这会造成用户体验感很差。

```
   /**
     * 1.保存用户信息到 redis中
     * 2. 随机生成token， 作为登录令牌
     * 3. 将user对象转成hashmap去存储
     * 4. 存储
     * 5. 返回token
     */
    String token = UUID.randomUUID().toString(true);
    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
    Map<String, Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(),
            CopyOptions.create().setIgnoreNullValue(true).
                    setFieldValueEditor((filedName , fieldValue) -> fieldValue.toString()));  //将对象转成map

    stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,map);
    //存储完成设置有效期 30 min
    stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL ,TimeUnit.MINUTES);
```

通过redis实现来代替session实现了解决session共享的问题。



### 商户查询缓存
**使用缓存解决缓存雪崩、穿透等问题解决**

对于商品缓存查询的版本迭代
`V1.0`
``` String Iid = String.valueOf(id); //强转时会出现异常
//        1. 从redis中查询商铺的缓存
        String shopJson = stringRedisTemplate.opsForValue().get(Iid);
//        2. 判断redis中是否存在该id的商户
        if(StrUtil.isNotBlank(shopJson)){
//        3. 如果存在  ： 返回商户的信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        Shop shopN = getById(id);
//        4. 如果不存在:
//        4.1根据传入的id查询数据库 ，判断数据库中是否存在商户
        String key = CACHE_SHOP_KEY + id;
        if(shopN == null){
        //4.3 如果不存在就返回401
            return Result.fail("店铺不存在！");
        }
//        4.2数据库中 商户如果存在就将商户信息写入redis
        stringRedisTemplate.opsForValue().set(key,shopN.toString());
```
通过上述方式可以解决商品缓存问题，但是还会有很多其他的问题出现


比如 : 
 - 数据同步问题
 - 缓存穿透问题(客户端请求的数据在缓存中和数据库中都不存在，这样缓存永远不会生效，这些请求都会打到数据库。)
 - ......

  
`V1.1`

```
对于一致性的需求不同，我们实现的方式也是有所差异的
低一致性需求： 使用内存淘汰机制，例如店铺类型的查询缓存
高一致性需求： 主动更新，并且使用超时剔除作为兜底方案。例如店铺的详情查询

//主动更新
        //1. 更新数据库
        updateById(shop);
        if(shop.getId() == null){
            return Result.fail("店铺id不能为空！！！");
        }
        Long id = shop.getId();
        String key = CACHE_SHOP_KEY + id;
        //删除缓存
        stringRedisTemplate.delete(key);

//超时剔除
stringRedisTemplate.opsForValue().set(key,shopN.toString(),30, TimeUnit.MINUTES);

......
```
`V1.2`

缓存击穿问题解决(缓存击穿问题也叫热点Key问题，就是一个被高并发访问并且缓存重建业务较复杂的key突然失效了，无数的请求访问会在瞬间给数据库带来巨大的冲击。)

`解决方案` : 互斥锁、逻辑过期

详细实现请看代码(controller包下的queryShopById方法)

### 用户签到
在 Redis 中，BitMap 是一种特殊的数据结构，它可以用来表示一组二进制位，其中每个二进制位只有 0 或 1 两种状态。BitMap 通常被用来进行高效的计数、去重等操作，而在用户签到功能中，我们可以将每个用户的签到情况用一个二进制位来表示，从而实现快速查询用户的签到情况。

 BitMap 的基本原理就是用一个bit 位来存放某种状态。通过使用BitMap则会大大减少内存的消耗
签到则为1， 未签到就是0

``` 
    //1. 获取用户
    Long userId = UserHolder.getUser().getId();
    //2. 获取日期
    LocalDateTime now = LocalDateTime.now();
    //3. 拼接key
    String key ="sign:" + userId + now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
    //4. 获取今天是本月的第几天
    int nowDayOfMonth = now.getDayOfMonth();
    //5. 写入redis setbit key offset 1
    stringRedisTemplate.opsForValue().setBit(key,nowDayOfMonth - 1,true);

```

### 商品缓存分类
对于商品分类页面的数据，因为这种数据不需要客户去登录，所以任何用户都可以访问，但是如果个别用户出现恶意涮屏或者说是大量用户同时浏览商品，那么就会造成大量服务达到数据库中，导致数据库访问压力过大。

通过使用redis，我们就可以将用户经常访问的数据放入缓存中，在设置过期时间(降低缓存压力)，那么对于数据库的访问就会大大降低。从而实现商品缓存分类
```
 List<ShopType> sort = query().orderByAsc("sort").list();
        for(ShopType item : sort){
            stringRedisTemplate.opsForList().rightPush("shopTypeList", JSONUtil.toJsonStr(item));
        }
```

### 好物分享
好物分享主要是实现文章、照片、短视频(未实现)等的发布功能
```
/**
     *
     * @param image 接收文件的地址
     * @return
     */
    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
```
其中还实现了文章查询等一系列操作


### 博文点赞
对于文章点赞功能我们需要考虑很多要素如 :一个用户只能点赞一个博文、用户必须登录后才能点赞、重读点击点赞按钮就会取消点赞等等

如果我们使用数据库的话就会有大量的请求访问数据库，这样将会造成数据库访问压力过大。

 这里我们也会用到redis来实现用户点赞功能，同时设置时效(比如30min)。当30min内没有任何用户点赞，那么就将点赞量写入数据库，然后删除缓存`(降低缓存的存储压力)`， 如此往复，既能减少对数据库的访问，也能降低缓存的压力。

同时实现了点赞用户显示、点赞排行榜等

### 好友关注
对于关注功能，我们需要使用一张博主和关注者之间的关联表，通过这张表我们才能查询到博主对应的关注者的id，从而查询到关注者信息

优化 : 将关注的用户(粉丝)存入redis中，使用set集合

```
 //todo 关注还是取关
    @Override
    public Result follow(Long followUserId, boolean isFollow) {
        //1. 获取登录的用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //1。 判断到底是关注还是取关
        /**
         * 当需要关注时，加入redis
         */
        if(isFollow){
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSecc = save(follow);
            if(isSecc){
                //把当前用户的id，放入redis的set集合中
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }
        //取消关注
        else{
            boolean isSucc = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSucc){
                //把关注的用户id从redis中移除
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
        
    }

```
使用set集合的原因就是我们可以通过set集合求交集的特征来实现共同关注




### ......

## 后期可以优化部分 
- SpringSecurity实现登录优化
- 使用布隆过滤解决缓存穿透问题，它的优点: 内存占用较少，没有多余key
- 可以试着自己封装缓存工具
- 对于缓存雪崩，以后试着用利用Redis集群提高服务的可用性、 给缓存业务添加降级限流策略 、 给业务添加多级缓存，而不是如今简单的实现给不同的Key的TTL添加随机值
- 点赞功能还可实现缓存更新的策略，也就是说使用redis实现点赞功能的同时设置缓存时效，当时间到了之后就将数据写入数据库，同时删除缓存`(既减少了对数据库的访问又降低了缓存的压力)`
- ......




其他相关功能正在实现...
## 本项目参考黑马点评开源项目实现
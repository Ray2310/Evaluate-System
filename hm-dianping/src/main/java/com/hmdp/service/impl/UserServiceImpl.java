package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * 服务实现类
 *  extends ServiceImpl<UserMapper, User> 继承这个mybatisplus中的实现类可以实现单表的crud
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserMapper userMapper;
    @Override
    public Result sendCode(String phone, HttpSession session) {
    // TODO 实现发送验证码方法
        /**
         * 1. 校验手机号是否合格
         * 2. 不合格怎么做
         //合格..........
         * 1. 生成验证码
         * 2. 保存验证码到 session
         * 3. 发送验证码
         */
        //1. 校验手机号是否合格（一般使用正则表达式去校验） 这里我们封装到RefexUtils.isPhoneInvalid是否是无效手机号
        boolean isNotNumber = RegexUtils.isPhoneInvalid(phone);
        //不是手机号
        if(isNotNumber){
            return Result.fail("手机号格式错误 !!!");
        }
        //合格，是手机号
        //1. 生成验证码
        String code = RandomUtil.randomNumbers(6);
        /**-----------------
         * 保存验证码到redis中
         * 添加业务前缀
         * 设置验证码的有效期 2分钟
         */
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //-----------------
        log.debug("发送短信验证码成功 ! 验证码为  : [" + code + "]");    //需要调用阿里云的测试，暂时不是重点 ，无需实现
        //返回ok就行了
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // TODO 实现登录功能
        //1. 提交验证码和手机号，并且进行判断是否正确
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误！");
        }
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        //2. **正确 : **就继续 ，**错误**  : 返回验证码错误
        if(phone == null || !loginForm.getCode().equals(code)){
            return Result.fail("验证码错误！");
        }
        //3. 调用数据库查询用户是否存在
        User user = query().eq("phone", phone).one();

        //4. **存在的话** : 保存用户信息到session，**不存在** : 就跳转到注册页面 ，注册并保存到数据库
        if(user == null){
            user = createUserWithPhone( phone);
        }
        //保存
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
        /**
         * 如果用户30分钟内一直进行访问的话，那么有效期就会不断的变化，所以我么就需要再拦截器中设置，一旦用户点击，就是有了请求
         * 那么就重置30分钟，一直往复的设值，那么就实现了用户30分钟不点点击就删除token的设置
         */
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        //生成随机的用户名 用系统常量
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //保存
        save(user);
        return user;
    }
}

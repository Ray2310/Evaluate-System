package com.hmdp.config.Handler;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 点击某个请求， 就重置token时间的拦截器
 */
public class preHandler implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public preHandler(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1. 获取请求携带的token
        String token = request.getHeader("authorization");
        String key = LOGIN_USER_KEY+ token;
        //2. 基于token获取redis中用户
        //3. 判断用户是否存在 ,如果不存在直接放行，不进行下面的步骤
        if(StrUtil.isBlank(token)){
            return true;
        }
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        //将redis查询到的用户信息hashmap转换成user对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
        //4. 保存信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        //刷新token有效期
        stringRedisTemplate.expire(key,LOGIN_USER_TTL , TimeUnit.MINUTES);
        //放行
        return true;
    }
}

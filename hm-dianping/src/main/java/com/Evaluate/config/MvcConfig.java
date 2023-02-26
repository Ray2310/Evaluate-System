package com.Evaluate.config;

import com.Evaluate.config.Handler.LoginInterceptor;
import com.Evaluate.config.Handler.preHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 添加拦截器
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
        //排除不需要拦截的路径
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**"
                ).order(1);
        //先执行
        //拦截所有的请求 ，作用就是用户登录了就点击刷新token消失的时间
        registry.addInterceptor(new preHandler(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}

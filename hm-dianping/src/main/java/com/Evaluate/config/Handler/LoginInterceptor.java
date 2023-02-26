package com.Evaluate.config.Handler;
import com.Evaluate.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
/**
 * 配置登录校验拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {
    /**
     * 拦截请求， 仅需要判断是否需要拦截，不需要做其他的事情
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(UserHolder.getUser() == null){
            response.setStatus(401);
            return false;
        }
        //由用户，放行
        return true;
    }
}

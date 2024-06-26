package com.ssh.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class LoginInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1。判断是否需要拦截，即ThreadLocal中是否有用户
        if (UserHolder.getUser()==null){
            //没有用户，拦截，设置状态码
            response.setStatus(401);
            return false;
        }

        //2.有用户，放行
        return  true;
    }


}

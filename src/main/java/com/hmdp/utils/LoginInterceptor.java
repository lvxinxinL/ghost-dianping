package com.hmdp.utils;

import cn.hutool.extra.tokenizer.engine.hanlp.HanLPEngine;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 登录校验拦截器
 * @author Ghost
 * @version 1.0
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取 session
        HttpSession session = request.getSession();
        // 2. 获取 session 中的用户信息
        Object user = session.getAttribute("user");

        // 3. 判断用户是否存在
        if(user == null) {
            // 4. 不存在（未登录），返回 401
            response.setStatus(401);
            return false;
        }

        // 5. 存在，使用工具将用户信息保存到 ThreadLocal 中
        UserHolder.saveUser((UserDTO) user);

        // 6. 放行
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}

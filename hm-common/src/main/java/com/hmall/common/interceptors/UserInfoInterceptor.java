package com.hmall.common.interceptors;

import cn.hutool.core.util.StrUtil;
import com.hmall.common.utils.UserContext;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *     公共 MVC 拦截器。读取网关传来的 user-info，
 *     写入 UserContext，请求结束后清理。
 *     cart-service、trade-service、pay-service、user-service 里的业务代码都在用 UserContext.getUser()，不能删。
 */
public class UserInfoInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取登录用户信息
       String userInfo= request.getHeader("user-info");
        //2.判断是都获取了用户 。如果获取了 存入 TreadLocal
        if(!StrUtil.isEmpty(userInfo)){
            UserContext.setUser(Long.parseLong(userInfo));
        }
        //放行
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.removeUser();
    }
}

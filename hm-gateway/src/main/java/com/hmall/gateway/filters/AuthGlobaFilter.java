package com.hmall.gateway.filters;

import cn.hutool.core.text.AntPathMatcher;
import com.hmall.common.exception.UnauthorizedException;
import com.hmall.gateway.config.AuthProperties;
import com.hmall.gateway.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 *     网关登录校验过滤器。校验 authorization token，解析用户 id，然后向下游请求写入 user-info 请求头
 */
@Component
@RequiredArgsConstructor
public class AuthGlobaFilter implements GlobalFilter, Ordered {

    private final AuthProperties authProperties;
    private final JwtTool jwtTool;
    private  final AntPathMatcher antPathMatcher = new AntPathMatcher();
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //1.获取request
        ServerHttpRequest request = exchange.getRequest();
        //2.判断是否需要做登录拦截
        if(isExclue(request.getPath().toString())){
            //f放行
            return chain.filter(exchange);
        }
        //3。获取token
        String token=null;
        List<String> headers= request.getHeaders().get("authorization");
        if(headers != null && !headers.isEmpty()){
            token=headers.get(0);
        }
        authProperties.getIncludePaths();
        //4.校验并解析token
        Long userId=null;
        try{
             userId= jwtTool.parseToken(token);
        }catch (UnauthorizedException e){
            //拦截相应状态码为401
           ServerHttpResponse response= exchange.getResponse();
           response.setStatusCode(HttpStatus.UNAUTHORIZED);
           return response.setComplete();
        }
        //5.传递用户信息
        System.out.println("userId:"+userId);
        String userInfo=userId.toString();
        //mutate对下游请求作修改
        ServerWebExchange swe=exchange.mutate()
                .request(builder -> builder.header("user-info",userInfo))
                .build();
        //6.放行
        return chain.filter(swe);
    }

    private boolean isExclue(String path) {
        List<String> excludePaths = authProperties.getExcludePaths();
        if (excludePaths == null || excludePaths.isEmpty()) {
            return false;
        }
        for(String pattern:excludePaths){
            if (antPathMatcher.match(pattern,path)) {
                return  true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}

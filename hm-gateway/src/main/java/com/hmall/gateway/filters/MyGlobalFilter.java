package com.hmall.gateway.filters;


import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class MyGlobalFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //TODO 模拟登录校验逻辑
        HttpHeaders headers=exchange.getRequest().getHeaders();
        System.out.println("headers: "+headers);
        //放行
        System.out.println("MyGlobalFilter........");
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0;//数值越小 级别越高
    }
}

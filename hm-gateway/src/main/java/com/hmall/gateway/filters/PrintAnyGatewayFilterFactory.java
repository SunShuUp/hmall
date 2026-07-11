package com.hmall.gateway.filters;

import lombok.Data;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class PrintAnyGatewayFilterFactory extends AbstractGatewayFilterFactory<PrintAnyGatewayFilterFactory.Config> {

    @Override
    public GatewayFilter apply(Config  config) {
        return new  OrderedGatewayFilter(new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                //编辑过滤器逻辑
                System.out.println("PrintAnyGatewayFilterFactory........");
                System.out.println(config.getA());
                System.out.println(config.getB());
                System.out.println(config.getC());
                return chain.filter(exchange);
            }
        },1);
    }


    //自定义配置属性
    @Data
    public static  class  Config{
        private String a;
        private String b;
        private String c;
    }
    //将变量名称依次返回 顺序很重要 将来读取参数时需要按照顺序获取
    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("a","b","c");
    }

    //将cofig字节码传递给父类 。弗雷负责帮我们读取myaml
    public PrintAnyGatewayFilterFactory(){
        super(Config.class);
    }
}

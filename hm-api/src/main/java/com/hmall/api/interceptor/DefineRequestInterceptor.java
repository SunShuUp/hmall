package com.hmall.api.interceptor;


import com.hmall.common.utils.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;

/**
 *    Feign 请求拦截器。
 *    服务之间调用时，把当前线程里的 UserContext 继续透传成 user-info 请求头。
 *    cart/trade/pay 的 @EnableFeignClients 都配置了它，建议保留。
 */
public class DefineRequestInterceptor {
    @Bean
    public RequestInterceptor interceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate requestTemplate) {
                Long userId=UserContext.getUser();
                if(userId!=null){
                    requestTemplate.header("user-info",String.valueOf(userId));
                }
            }
        };
    }
}

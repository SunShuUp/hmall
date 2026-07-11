package com.hmall.api.interceptor;

import com.hmall.common.utils.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;

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

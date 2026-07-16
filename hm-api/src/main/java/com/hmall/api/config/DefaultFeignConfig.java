package com.hmall.api.config;

import com.hmall.api.client.ItemClientFallback;
import feign.Logger;
import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;

@Slf4j
public class DefaultFeignConfig {
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
    @Bean
    public ItemClientFallback itemClientFallback() {
        return new ItemClientFallback();
    }
}

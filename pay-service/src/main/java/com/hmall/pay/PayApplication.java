package com.hmall.pay;

import com.hmall.api.config.DefaultFeignConfig;
import com.hmall.api.interceptor.DefineRequestInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.hmall.pay.mapper")
@EnableFeignClients(defaultConfiguration = {DefaultFeignConfig.class, DefineRequestInterceptor.class},basePackages = "com.hmall.api.client")
public class PayApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(PayApplication.class, args);
    }
}

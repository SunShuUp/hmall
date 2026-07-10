package com.hmall.cart;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * Hello world!
 *
 */
@SpringBootApplication
@MapperScan("com.hmall.cart.mapper")
public class CartApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(CartApplication.class, args);
    }
    @Bean
    public RestTemplate restTemplate()
    {
        return new RestTemplate();
    }
}

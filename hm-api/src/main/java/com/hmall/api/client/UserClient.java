package com.hmall.api.client;

import feign.Param;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("user-service")
public interface UserClient {
    @PutMapping("/users/money/deduct")
    void deductMoney(@javax.validation.constraints.NotNull(message = "支付密码")@RequestParam("pw") String pw, @RequestParam("amount") Integer amount);
}

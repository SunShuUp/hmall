package com.hmall.api.client;

import com.hmall.api.dto.ItemDTO;
import com.hmall.api.dto.OrderDetailDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Slf4j
public class ItemClientFallback  implements FallbackFactory<ItemClient> {
    @Override
    public ItemClient create(Throwable cause) {
        return new ItemClient() {

            @Override
            public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
                log.error("fallback ItemClientFallback queryItemByIds error", cause);
                return Collections.emptyList();
            }

            @Override
            public void deductStock(List<OrderDetailDTO> detailDTOS) {
                log.error("fallback ItemClientFallback deductStock error", cause);
                throw new RuntimeException("fallback ItemClientFallback deductStock error");
            }
        };
    }
}

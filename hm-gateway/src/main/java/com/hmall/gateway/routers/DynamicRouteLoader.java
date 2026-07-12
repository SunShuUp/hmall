package com.hmall.gateway.routers;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@Component
@Slf4j
@RequiredArgsConstructor
public class DynamicRouteLoader {
    private final NacosConfigManager nacosConfigManager;
    private  final RouteDefinitionWriter routeDefinitionWriter;
    private final  String dateId="gateway-routes.json";
    private  final String group="DEFAULT_GROUP";
    //保存更新过的路由和id
    private final Set<String> routeIds=new HashSet<>();

    @PostConstruct//初始化之后执行
    public void initRouterConfigListener() throws NacosException {
        //1.项目启动 先啦取一次配置 并且添加配置监听器
       String configInfo= nacosConfigManager.getConfigService()
                .getConfigAndSignListener(dateId, group, 5000, new Listener() {
                    @Override
                    public Executor getExecutor() {
                        return null;
                    }

                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        //2.监听到配置变更 。需要去更新路由表
                        updateConfigInfo(configInfo);
                    }
                });
       //3.第一次读取配置也需要更新路由表
        updateConfigInfo(configInfo);
    }
    public void updateConfigInfo(String configInfo) {
        log.debug("update config info:{}", configInfo);
        //1.反序列化
        List<RouteDefinition> routeDefinitions = JSONUtil.toList(configInfo, RouteDefinition.class);
        //2.更新之前先清空旧路由
        for (String routeId : routeIds) {
            //2.1清空旧的路由
            routeDefinitionWriter.delete(Mono.just(routeId)).subscribe();
        }
        routeIds.clear();
        //2。2判断是否有新的路由要更新
        if(CollectionUtils.isEmpty(routeDefinitions)){
            return;
        }
        //3.更细路由
        routeDefinitions.forEach(routeDefinition -> {
            //3.1更新路由
            routeDefinitionWriter.save(Mono.just(routeDefinition)).subscribe();
            //3.2记录路由ID 。方便将来删除
            routeIds.add(routeDefinition.getId());
        });
    }
}

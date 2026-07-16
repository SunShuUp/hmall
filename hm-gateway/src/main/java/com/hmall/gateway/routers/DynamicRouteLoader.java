package com.hmall.gateway.routers;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 这段代码的作用：从 Nacos 读取网关路由配置，并在配置变化时动态更新 Spring Cloud Gateway 的路由表。
 *
 *   它的核心目标是：不用重启网关，只要修改 Nacos 里的 gateway-routes.json，网关路由就能自动刷新。
 *
 *
 *
 *
 *    项目启动
 *     -> 执行 @PostConstruct 方法
 *     -> 从 Nacos 拉取 gateway-routes.json
 *     -> 注册 Nacos 监听器
 *     -> 调用 updateConfigInfo
 *     -> 删除旧路由
 *     -> 添加新路由
 *
 *   配置变更流程：
 *
 *   Nacos 中 gateway-routes.json 被修改
 *     -> Listener.receiveConfigInfo 收到新配置
 *     -> 调用 updateConfigInfo
 *     -> 删除之前动态添加的路由
 *     -> 添加最新配置中的路由
 *
 *
 *
 */
@Component//这里表示这是一个 Spring Bean，会被 Spring 自动创建。
@Slf4j
@RequiredArgsConstructor
public class DynamicRouteLoader {
    private final NacosConfigManager nacosConfigManager;// 用于访问 Nacos 配置中心。
    private  final RouteDefinitionWriter routeDefinitionWriter;//  用于向 Spring Cloud Gateway 添加或删除路由。
    private final ApplicationEventPublisher publisher;
    private final  String dataId="gateway-routes.json";//  这里指定了 Nacos 配置文件的位置：
    private  final String group="DEFAULT_GROUP";
    //保存更新过的路由和id    这个集合用来保存当前动态加载过的路由 ID。
    //
    //  因为下一次 Nacos 配置更新时，需要先删除旧路由，再添加新路由。如果不记录旧路由 ID，就不知道要删哪些。
    //private final Set<String> routeIds=new HashSet<>(); 非线程安全 //
    private final Set<String> routeIds = ConcurrentHashMap.newKeySet();

    @PostConstruct//  @PostConstruct 表示：Spring 创建完这个 Bean，并完成依赖注入之后，会自动执行这个方法。  也就是说，网关项目启动时，会自动执行这里的逻辑。

    public void initRouterConfigListener() throws NacosException {
        //1.项目启动 先啦取一次配置 并且添加配置监听器
        //1. 从 Nacos 拉取一次 gateway-routes.json 配置
        //  2. 同时注册监听器，以后配置变了会收到通知
       String configInfo= nacosConfigManager.getConfigService()
                .getConfigAndSignListener(dataId, group, 5000, new Listener() {
                    @Override
                    public Executor getExecutor() {
                        return null;
                    }

                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        //2.监听到配置变更，需要去更新路由表
                        //这里调用的是最复杂、最稳妥的版本：updateConfigInfo(configInfo)
                        updateConfigInfo(configInfo);
                    }
                });
       //3.第一次读取配置也需要更新路由表
       //前面注册监听器只能处理“以后发生的变化”，但是项目刚启动时也需要马上加载一次已有配置。
       //这里也调用最复杂、最稳妥的版本：updateConfigInfo(configInfo)
        updateConfigInfo(configInfo);
    }

    /**
     * 【复杂优化版】
     *
     * 这是当前真正使用的版本：
     * 1. Nacos 监听器调用的是这个方法
     * 2. 项目启动时第一次加载配置，调用的也是这个方法
     *
     * 这个版本解决的问题：
     * 1. synchronized：防止 Nacos 短时间连续推送配置时，多个线程同时更新路由
     * 2. block()：等待删除旧路由完成之后，再继续添加新路由
     * 3. Flux.concatMap()：按顺序执行删除、保存操作
     * 4. RefreshRoutesEvent：通知 Spring Cloud Gateway 刷新路由缓存
     * 5. StrUtil.isBlank()：防止 Nacos 配置为空字符串时反序列化报错
     *
     * @param configInfo Nacos 中 gateway-routes.json 的配置内容
     */
    public synchronized void updateConfigInfo(String configInfo) {
        log.debug("update config info:{}", configInfo);

        //1. 把 Nacos 中的 JSON 字符串转成路由对象列表
        //如果配置为空，就当作空集合处理，表示清空动态路由
        List<RouteDefinition> routeDefinitions = StrUtil.isBlank(configInfo)
                ? Collections.emptyList()
                : JSONUtil.toList(configInfo, RouteDefinition.class);

        //2. 复制一份旧路由 id
        //不要直接遍历 routeIds，因为后面会 clear，复制出来更安全
        Set<String> oldRouteIds = new HashSet<>(routeIds);

        //3. 删除所有旧路由
        //Flux.fromIterable(oldRouteIds)：把旧路由 id 集合变成一个响应式流
        //concatMap(...)：一个一个删除，前一个删除完成后，才会删除下一个
        //onErrorResume(...)：某个旧路由删除失败时，记录日志，然后继续删除其它路由
        //then()：忽略每次删除的结果，只关心整批删除有没有执行完
        //block()：等待整批删除真正完成，再继续往下执行
        Flux.fromIterable(oldRouteIds)
                .concatMap(routeId -> routeDefinitionWriter.delete(Mono.just(routeId))
                        .onErrorResume(e -> {
                            log.warn("delete route failed, id={}", routeId, e);
                            return Mono.empty();
                        }))
                .then()
                .block();

        //4. 旧路由已经删除完成，所以清空本地记录
        routeIds.clear();

        //5. 如果 Nacos 中没有配置新路由，说明只需要清空旧路由
        //清空后发布刷新事件，让 Gateway 立即刷新缓存
        if (CollectionUtils.isEmpty(routeDefinitions)) {
            publisher.publishEvent(new RefreshRoutesEvent(this));
            return;
        }

        //6. 保存新的路由
        //concatMap(...)：一个一个保存，前一个保存完成后，才会保存下一个
        //doOnNext(...)：保存成功后，把路由 id 记录到 routeIds，方便下次删除
        //then()：忽略每次保存的结果，只关心整批保存有没有执行完
        //block()：等待整批保存真正完成，再继续往下执行
        Flux.fromIterable(routeDefinitions)
                .concatMap(routeDefinition -> routeDefinitionWriter.save(Mono.just(routeDefinition))
                        .thenReturn(routeDefinition.getId()))
                .doOnNext(routeIds::add)
                .then()
                .block();

        //7. 发布刷新事件，通知 Gateway 重新加载路由缓存
        publisher.publishEvent(new RefreshRoutesEvent(this));

        if (log.isDebugEnabled()) {
            log.debug("dynamic routes refreshed, ids={}", routeIds);
        }
    }

    /**
     * 【简单优化版】
     *
     * 这个版本比原始版本好懂，也比原始版本安全：
     * 1. synchronized：防止多个线程同时更新路由
     * 2. block()：等待删除或保存真正完成后，再继续执行下一步
     * 3. RefreshRoutesEvent：通知 Gateway 刷新路由缓存
     *
     * 和复杂版相比：
     * 1. 它不用 Flux.concatMap()
     * 2. 它用普通 for 循环，阅读起来更直接
     * 3. 适合学习和调试
     *
     * 注意：
     * 当前监听器没有调用这个方法。
     * 如果你想使用简单版，可以把监听器中的 updateConfigInfo(configInfo)
     * 改成 updateConfigInfoSimple(configInfo)。
     *
     * @param configInfo Nacos 中 gateway-routes.json 的配置内容
     */
    public synchronized void updateConfigInfoSimple(String configInfo) {
        log.debug("update config info by simple version:{}", configInfo);

        //1. 把 Nacos 中的 JSON 字符串转成路由对象列表
        List<RouteDefinition> routeDefinitions = StrUtil.isBlank(configInfo)
                ? Collections.emptyList()
                : JSONUtil.toList(configInfo, RouteDefinition.class);

        //2. 删除旧路由
        Set<String> oldRouteIds = new HashSet<>(routeIds);
        for (String routeId : oldRouteIds) {
            try {
                //block() 表示等这个删除动作真正执行完，再继续往下走
                routeDefinitionWriter.delete(Mono.just(routeId)).block();
            } catch (Exception e) {
                log.warn("delete route failed, id={}", routeId, e);
            }
        }
        routeIds.clear();

        //3. 如果没有新路由，就只刷新 Gateway 路由缓存，然后结束
        if (CollectionUtils.isEmpty(routeDefinitions)) {
            publisher.publishEvent(new RefreshRoutesEvent(this));
            return;
        }

        //4. 添加新路由
        for (RouteDefinition routeDefinition : routeDefinitions) {
            //block() 表示等这个保存动作真正执行完，再记录路由 id
            routeDefinitionWriter.save(Mono.just(routeDefinition)).block();
            routeIds.add(routeDefinition.getId());
        }

        //5. 通知 Gateway 刷新路由缓存
        publisher.publishEvent(new RefreshRoutesEvent(this));

        if (log.isDebugEnabled()) {
            log.debug("dynamic routes refreshed by simple version, ids={}", routeIds);
        }
    }

    /**
     * 【原始版本】
     *
     * 这个方法保留你最开始的写法，方便对比学习。
     *
     * 原始版本的特点：
     * 1. 代码短，容易写
     * 2. 使用 subscribe() 触发删除和保存
     *
     * 原始版本的问题：
     * 1. subscribe() 只表示“开始执行”，不会等待执行完成
     * 2. 删除旧路由还没完成时，代码可能已经开始保存新路由
     * 3. Nacos 如果连续推送配置，多个更新流程可能交叉执行
     * 4. 没有发布 RefreshRoutesEvent，Gateway 路由缓存可能不会立刻刷新
     * 5. routeIds 如果使用普通 HashSet，在并发场景下不安全
     *
     * 注意：
     * 当前监听器没有调用这个方法。
     * 这个方法只是保留原始写法，方便你对比复杂版和简单版。
     *
     * @param configInfo Nacos 中 gateway-routes.json 的配置内容
     */
    public void updateConfigInfoOriginal(String configInfo) {
        log.debug("update config info by original version:{}", configInfo);

        //1. 反序列化，把 Nacos 中的 JSON 字符串转成路由对象列表
        List<RouteDefinition> routeDefinitions = JSONUtil.toList(configInfo, RouteDefinition.class);

        //2. 更新之前先清空旧路由
        for (String routeId : routeIds) {
            //2.1 删除旧路由
            //subscribe() 会触发删除动作，但是不会等待删除完成
            routeDefinitionWriter.delete(Mono.just(routeId)).subscribe();
        }
        routeIds.clear();

        //3. 判断是否有新的路由要更新
        if (CollectionUtils.isEmpty(routeDefinitions)) {
            return;
        }

        //4. 添加新路由
        routeDefinitions.forEach(routeDefinition -> {
            //4.1 保存新路由
            //subscribe() 会触发保存动作，但是不会等待保存完成
            routeDefinitionWriter.save(Mono.just(routeDefinition)).subscribe();

            //4.2 记录路由 id，方便下一次配置变更时删除
            routeIds.add(routeDefinition.getId());
        });
    }
}

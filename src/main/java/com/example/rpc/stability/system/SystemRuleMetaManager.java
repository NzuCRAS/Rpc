package com.example.rpc.stability.system;

import com.example.rpc.protocol.JsonSerializer;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多粒度系统保护参数元数据管理，支持ZK热更
 */
public class SystemRuleMetaManager {
    private static final String ZK_ADDRESS = "localhost:2181";
    private static final String META_PATH = "/sentinel/rpc-system-rule-meta";
    private static final JsonSerializer serializer = new JsonSerializer();

    // 多粒度配置缓存
    private static final Map<String, SystemRuleMeta> metaMap = new ConcurrentHashMap<>();
    private static CuratorFramework zkClient;

    public static void init() throws Exception {
        zkClient = CuratorFrameworkFactory.newClient(
                ZK_ADDRESS,
                new ExponentialBackoffRetry(1000, 3)
        );
        zkClient.start();
        if (zkClient.checkExists().forPath(META_PATH) == null) {
            Map<String, SystemRuleMeta> defaultMap = Map.of("default", new SystemRuleMeta());
            byte[] bytes = serializer.serialize(defaultMap);
            zkClient.create().creatingParentContainersIfNeeded().forPath(META_PATH, bytes);
        }
        watchMetaNode();
        refreshMeta();
    }

    private static void watchMetaNode() {
        CuratorCache cache = CuratorCache.build(zkClient, META_PATH);
        cache.listenable().addListener(
                CuratorCacheListener.builder()
                        .forCreatesAndChanges((childData, event) -> {
                            if (childData != null && childData.getPath().equals(META_PATH)) {
                                refreshMeta();
                            }
                        }).build()
        );
        cache.start();
    }

    private static void refreshMeta() {
        try {
            byte[] data = zkClient.getData().forPath(META_PATH);
            if (data != null && data.length > 0) {
                Map<String, SystemRuleMeta> map = serializer.mapDeserialize(data, SystemRuleMeta.class);
                metaMap.clear();
                metaMap.putAll(map);
                System.out.println("[SystemRuleMetaManager] Updated meta map: " + metaMap);
            }
        } catch (Exception e) {
            System.err.println("[SystemRuleMetaManager] Failed to refresh meta: " + e.getMessage());
        }
    }

    public static SystemRuleMeta getMeta(String resourceKey) {
        // 优先级：parameter > ip > interface > default
        if (metaMap.containsKey(resourceKey)) return metaMap.get(resourceKey);
        if (resourceKey.startsWith("parameter:") && metaMap.containsKey("parameter")) return metaMap.get("parameter");
        if (resourceKey.startsWith("ip:") && metaMap.containsKey("ip")) return metaMap.get("ip");
        if (resourceKey.startsWith("interface:") && metaMap.containsKey("interface")) return metaMap.get("interface");
        return metaMap.get("default");
    }
}
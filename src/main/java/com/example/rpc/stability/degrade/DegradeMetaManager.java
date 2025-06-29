package com.example.rpc.stability.degrade;

import com.example.rpc.protocol.JsonSerializer;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DegradeMetaManager {
    private static final String ZK_ADDRESS = "localhost:2181";
    private static final String META_PATH = "/sentinel/rpc-degrade-meta";
    private static final JsonSerializer serializer = new JsonSerializer();

    private static final Map<String, DegradeRuleMeta> metaMap = new ConcurrentHashMap<>();
    private static CuratorFramework zkClient;

    public static void init() throws Exception {
        zkClient = CuratorFrameworkFactory.newClient(
                ZK_ADDRESS,
                new ExponentialBackoffRetry(1000, 3)
        );
        zkClient.start();
        if (zkClient.checkExists().forPath(META_PATH) == null) {
            Map<String, DegradeRuleMeta> defaultMap = Map.of("default", new DegradeRuleMeta());
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
                Map<String, DegradeRuleMeta> map = serializer.mapDeserialize(data, DegradeRuleMeta.class);
                metaMap.clear();
                metaMap.putAll(map);
                System.out.println("[DegradeMetaManager] Updated meta map: " + metaMap);
            }
        } catch (Exception e) {
            System.err.println("[DegradeMetaManager] Failed to refresh meta: " + e.getMessage());
        }
    }

    public static DegradeRuleMeta getMeta(String resourceKey) {
        // 优先级：parameter > ip > interface > default
        if (metaMap.containsKey(resourceKey)) return metaMap.get(resourceKey);
        if (resourceKey.startsWith("parameter:") && metaMap.containsKey("parameter")) return metaMap.get("parameter");
        if (resourceKey.startsWith("ip:") && metaMap.containsKey("ip")) return metaMap.get("ip");
        if (resourceKey.startsWith("interface:") && metaMap.containsKey("interface")) return metaMap.get("interface");
        return metaMap.get("default");
    }
}
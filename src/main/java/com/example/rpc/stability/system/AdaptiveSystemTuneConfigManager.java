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
 * 多粒度自适应调优参数配置中心，支持ZK热更
 */
public class AdaptiveSystemTuneConfigManager {
    private static final String ZK_ADDRESS = "localhost:2181";
    private static final String CONFIG_PATH = "/sentinel/rpc-system-tune-config";
    private static final JsonSerializer serializer = new JsonSerializer();

    private static final Map<String, AdaptiveSystemTuneConfig> configMap = new ConcurrentHashMap<>();
    private static CuratorFramework zkClient;

    public static void init() throws Exception {
        zkClient = CuratorFrameworkFactory.newClient(
                ZK_ADDRESS,
                new ExponentialBackoffRetry(1000, 3)
        );
        zkClient.start();
        if (zkClient.checkExists().forPath(CONFIG_PATH) == null) {
            Map<String, AdaptiveSystemTuneConfig> defaultConfig = Map.of("default", new AdaptiveSystemTuneConfig());
            byte[] bytes = serializer.serialize(defaultConfig);
            zkClient.create().creatingParentContainersIfNeeded().forPath(CONFIG_PATH, bytes);
        }
        watchConfigNode();
        refreshConfig();
    }

    private static void watchConfigNode() {
        CuratorCache cache = CuratorCache.build(zkClient, CONFIG_PATH);
        cache.listenable().addListener(
                CuratorCacheListener.builder()
                        .forCreatesAndChanges((childData, event) -> {
                            if (childData != null && childData.getPath().equals(CONFIG_PATH)) {
                                refreshConfig();
                            }
                        }).build()
        );
        cache.start();
    }

    private static void refreshConfig() {
        try {
            byte[] data = zkClient.getData().forPath(CONFIG_PATH);
            if (data != null && data.length > 0) {
                Map<String, AdaptiveSystemTuneConfig> map = serializer.mapDeserialize(data, AdaptiveSystemTuneConfig.class);
                configMap.clear();
                configMap.putAll(map);
                System.out.println("[AdaptiveSystemTuneConfigManager] Updated tune config: " + configMap);
            }
        } catch (Exception e) {
            System.err.println("[AdaptiveSystemTuneConfigManager] Failed to refresh config: " + e.getMessage());
        }
    }

    public static AdaptiveSystemTuneConfig getConfig(String resourceKey) {
        if (configMap.containsKey(resourceKey)) return configMap.get(resourceKey);
        if (resourceKey.startsWith("parameter:") && configMap.containsKey("parameter")) return configMap.get("parameter");
        if (resourceKey.startsWith("ip:") && configMap.containsKey("ip")) return configMap.get("ip");
        if (resourceKey.startsWith("interface:") && configMap.containsKey("interface")) return configMap.get("interface");
        return configMap.get("default");
    }
}
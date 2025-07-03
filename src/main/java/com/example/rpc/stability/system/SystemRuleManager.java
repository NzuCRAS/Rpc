package com.example.rpc.stability.system;

import com.example.rpc.protocol.JsonSerializer;
import com.example.rpc.stability.rule.SentinelRuleZkManager;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;

import com.alibaba.csp.sentinel.slots.system.SystemRule;

import java.util.ArrayList;
import java.util.List;

public class SystemRuleManager {
    private static final String ZK_ADDRESS = "localhost:2181";
    private static final String META_PATH = "/sentinel/rpc-system-rule-meta";
    private static final JsonSerializer serializer = new JsonSerializer();

    private static SystemRuleMeta currentMeta;

    public static void init() throws Exception {
        CuratorFramework zkClient = CuratorFrameworkFactory.newClient(
                ZK_ADDRESS,
                new ExponentialBackoffRetry(1000, 3)
        );
        zkClient.start();
        if (zkClient.checkExists().forPath(META_PATH) == null) {
            SystemRuleMeta defaultMeta = new SystemRuleMeta();
            byte[] bytes = serializer.serialize(defaultMeta);
            zkClient.create().creatingParentContainersIfNeeded().forPath(META_PATH, bytes);
        }
        watchMetaNode(zkClient);
        refreshMeta(zkClient);
    }

    private static void watchMetaNode(CuratorFramework zkClient) {
        CuratorCache cache = CuratorCache.build(zkClient, META_PATH);
        cache.listenable().addListener(
                CuratorCacheListener.builder()
                        .forCreatesAndChanges((childData, event) -> {
                            if (childData != null && childData.getPath().equals(META_PATH)) {
                                refreshMeta(zkClient);
                            }
                        }).build()
        );
        cache.start();
    }

    private static void refreshMeta(CuratorFramework zkClient) {
        try {
            byte[] data = zkClient.getData().forPath(META_PATH);
            if (data != null && data.length > 0) {
                currentMeta = serializer.deserialize(data, SystemRuleMeta.class);
                System.out.println("[SystemRuleManager] Updated system rule meta: " + currentMeta);
                pushToSentinel(currentMeta);
            }
        } catch (Exception e) {
            System.err.println("[SystemRuleManager] Failed to refresh system meta: " + e.getMessage());
        }
    }

    private static void pushToSentinel(SystemRuleMeta meta) {
        List<SystemRule> rules = new ArrayList<>();
        SystemRule rule = new SystemRule();
        rule.setHighestSystemLoad(meta.highestSystemLoad);
        rule.setHighestCpuUsage(meta.highestCpuUsage);
        rule.setQps(meta.maxQps);
        rule.setMaxThread(meta.maxThread);
        rule.setAvgRt((long) meta.maxAvgRt);
        rules.add(rule);
        // 推送到Sentinel，复用你的ZK推送器
        SentinelRuleZkManager.pushSystemRules(rules, System.currentTimeMillis());
    }
}
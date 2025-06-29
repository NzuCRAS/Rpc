package com.example.rpc.stability.rule;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.example.rpc.protocol.JsonSerializer;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * 基于Zookeeper的Sentinel限流规则动态推送与热更新工具
 */
public class SentinelRuleZkManager {
    private static final String ZK_ADDRESS = "localhost:2181"; // ZK地址，按需修改
    private static final String RULE_PATH = "/sentinel/rpc-limit-rules";
    private static final String META_PATH = "/sentinel/rpc-limit-rules-meta";
    private static final String DEGRADE_RULE_PATH = "/sentinel/rpc-degrade-rules";
    private static final String DEGRADE_META_PATH = "/sentinel/rpc-degrade-rules-meta";
    private static CuratorFramework zkClient;
    private static final JsonSerializer serializer = new JsonSerializer();
    public enum RuleType { FLOW, DEGRADE, SYSTEM }

    public static void init() throws Exception {
        zkClient = CuratorFrameworkFactory.newClient(
                ZK_ADDRESS,
                new ExponentialBackoffRetry(1000, 3)
        );
        zkClient.start();
        // 创建节点（如不存在），便于后续管理
        if (zkClient.checkExists().forPath(RULE_PATH) == null) {
            zkClient.create().creatingParentContainersIfNeeded().forPath(RULE_PATH, "[]".getBytes(StandardCharsets.UTF_8));
        }
        if (zkClient.checkExists().forPath(META_PATH) == null) {
            zkClient.create().creatingParentContainersIfNeeded().forPath(META_PATH, "".getBytes(StandardCharsets.UTF_8));
        }
        // 启动监听
        watchRuleNode();
        // 启动时先拉取一次规则
        refreshRules();
        refreshDegradeRules();
    }

    private static void watchRuleNode() {
        CuratorCache curatorCache = CuratorCache.build(zkClient, RULE_PATH);
        curatorCache.listenable().addListener(
                CuratorCacheListener.builder()
                        .forPathChildrenCache(RULE_PATH, zkClient, (client, event) -> {
                            // 对于子节点事件，这里可不做处理（本例只监听节点本身）
                        })
                        .forCreatesAndChanges((childData, event) -> {
                            // 节点创建或数据变更
                            if (childData != null && childData.getPath().equals(RULE_PATH)) {
                                refreshRules();
                            }
                        })
                        .build()
        );
        curatorCache.start();
    }

    private static void refreshRules() {
        try {
            byte[] data = zkClient.getData().forPath(RULE_PATH);
            List<FlowRule> rules = serializer.listDeserialize(data, FlowRule.class);
            FlowRuleManager.loadRules(!rules.isEmpty() ? rules : Collections.emptyList());
            System.out.println("[Sentinel] Dynamic Rule Update: " + rules);
        } catch (Exception e) {
            System.err.println("[Sentinel] Pull / Parse Rule Failed: " + e.getMessage());
        }
    }

    public static void pushDegradeRules(List<DegradeRule> rules, long ts) {
        try {
            byte[] rulesBytes = serializer.serialize(rules);
            zkClient.setData().forPath(DEGRADE_RULE_PATH, rulesBytes);
            zkClient.setData().forPath(DEGRADE_META_PATH, String.valueOf(ts).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[Sentinel] 推送熔断规则失败: " + e.getMessage());
        }
    }

    // 建议在init和watchRuleNode里添加degrade规则的监听和refresh
    private static void refreshDegradeRules() {
        try {
            byte[] data = zkClient.getData().forPath(DEGRADE_RULE_PATH);
            List<DegradeRule> rules = serializer.listDeserialize(data, DegradeRule.class);
            DegradeRuleManager.loadRules(!rules.isEmpty() ? rules : Collections.emptyList());
            System.out.println("[Sentinel] Dynamic Degrade Rule Update: " + rules);
        } catch (Exception e) {
            System.err.println("[Sentinel] Pull / Parse Degrade Rule Failed: " + e.getMessage());
        }
    }

    // 推送规则并写入更新时间戳
    public static void pushRules(List<FlowRule> rules, long ts) {
        try {
            byte[] rulesBytes = serializer.serialize(rules);
            zkClient.setData().forPath(RULE_PATH, rulesBytes);
            // 附加更新时间戳
            zkClient.setData().forPath(META_PATH, String.valueOf(ts).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[Sentinel] 推送限流规则失败: " + e.getMessage());
        }
    }

    public static void pushSystemRules(List<SystemRule> rules, long ts) {

    }
}
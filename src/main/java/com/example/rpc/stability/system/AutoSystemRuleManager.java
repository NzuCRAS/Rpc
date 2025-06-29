package com.example.rpc.stability.system;



import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.example.rpc.stability.rule.SentinelRuleZkManager;
import com.example.rpc.stability.stat.ResourceStat;
import com.example.rpc.stability.stat.ResourceStatManager;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AutoSystemRuleManager {
    private final long intervalMs = 60_000;
    private final List<String> systemTypes = List.of("interface", "ip", "parameter");

    public void start() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                List<SystemRule> rules = new ArrayList<>();
                SystemStatSnapshot sysStat = SystemStatCollector.collect();
                Map<String, Map<String, ResourceStat>> allStats = ResourceStatManager.getAllStats();
                for (String type : systemTypes) {
                    Map<String, ResourceStat> statMap = allStats.get(type);
                    if (statMap == null) continue;
                    for (Map.Entry<String, ResourceStat> entry : statMap.entrySet()) {
                        String resource = type + ":" + entry.getKey();
                        ResourceStat stat = entry.getValue();

                        SystemRuleMeta baseMeta = SystemRuleMetaManager.getMeta(resource);
                        SystemRuleMeta meta = AdaptiveSystemTuner.tune(resource, sysStat, stat, baseMeta);

                        SystemRule rule = new SystemRule();
                        rule.setResource(meta.resourceKey);
                        rule.setHighestSystemLoad(meta.highestSystemLoad);
                        rule.setHighestCpuUsage(meta.highestCpuUsage);
                        rule.setQps(meta.maxQps);
                        rule.setMaxThread(meta.maxThread);
                        rule.setAvgRt((long) meta.maxAvgRt);

                        rules.add(rule);
                    }
                }
                long ts = System.currentTimeMillis();
                SentinelRuleZkManager.pushSystemRules(rules, ts);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }
}
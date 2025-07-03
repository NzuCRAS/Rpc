package com.example.rpc.stability.degrade;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.example.rpc.metrics.StatMetricsReporter;
import com.example.rpc.stability.rule.SentinelRuleZkManager;
import com.example.rpc.stability.stat.ResourceStat;
import com.example.rpc.stability.stat.ResourceStatManager;


import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 自动生成并推送熔断规则，支持动态配置/热更新
 * 集成动态热更新配置
 */
public class AutoDegradeRuleManager {
    private final long intervalMs = 10_000;
    private final List<String> degradeTypes = List.of("interface", "ip", "parameter");

    public void start() throws Exception {
        System.out.println("AutoDegradeRuleManager start");
        DegradeMetaManager.init();
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                List<DegradeRule> rules = new ArrayList<>();
                Map<String, Map<String, ResourceStat>> allStats = ResourceStatManager.getAllStats();
                for (String type : degradeTypes) {
                    Map<String, ResourceStat> statMap = allStats.get(type);
                    if (statMap == null) continue;
                    for (Map.Entry<String, ResourceStat> entry : statMap.entrySet()) {
                        String resource = type + ":" + entry.getKey();
                        ResourceStat stat = entry.getValue();

                        // 先取多粒度meta
                        DegradeRuleMeta baseMeta = DegradeMetaManager.getMeta(resource);
                        // 进行自适应调优
                        DegradeRuleMeta meta = AdaptiveDegradeTuner.tune(resource, stat, baseMeta);

                        DegradeRule rule = new DegradeRule();
                        rule.setResource(resource);
                        switch (meta.type) {
                            case EXCEPTION_RATIO:
                                rule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
                                rule.setCount(meta.exceptionRatio);
                                break;
                            case RT:
                                rule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
                                rule.setCount(meta.rtThreshold);
                                break;
                            case EXCEPTION_COUNT:
                                rule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT);
                                rule.setCount(meta.exceptionCount);
                                break;
                        }
                        rule.setMinRequestAmount(meta.minRequestAmount);
                        rule.setStatIntervalMs(meta.statIntervalMs);
                        rule.setTimeWindow(meta.timeWindow);

                        rules.add(rule);
                    }
                }
                long ts = System.currentTimeMillis();
                SentinelRuleZkManager.pushDegradeRules(rules, ts);
                System.out.println("[AutoDegradeRuleManager] Pushed degrade rules at " + ts + ": " + rules);
                StatMetricsReporter.reportAll();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }
}
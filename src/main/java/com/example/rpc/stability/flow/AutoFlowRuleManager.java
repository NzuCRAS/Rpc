package com.example.rpc.stability.flow;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.example.rpc.stability.stat.ResourceStat;
import com.example.rpc.stability.stat.ResourceStatManager;
import com.example.rpc.stability.rule.SentinelRuleZkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 定时汇总各粒度的统计数据，自动生成限流规则并推送到配置中心
 */
public class AutoFlowRuleManager {
    private final long intervalMs = 60_000; // 推送周期
    // 需要限流的粒度类型，可扩展
    private final List<String> limitTypes = List.of("interface", "ip", "parameter");

    /**
     * 综合多因子自适应限流算法
     * 规则：
     *   - QPS是基础，乘以不同粒度的系数
     *   - 响应时间高时系数递减
     *   - 异常率高时阈值收紧，极端时直接锁死
     */
    private double computeThreshold(String type, ResourceStat stat) {
        double qps = stat.getQps(intervalMs);
        double avgRt = stat.getAvgRt();
        double errorRate = stat.getErrorRate();

        // 粒度基准系数（可调优，interface一般放大，ip/parameter一般收紧）
        double baseFactor;
        switch (type) {
            case "ip":
                baseFactor = 1.2;
                break;
            case "parameter":
                baseFactor = 1.5;
                break;
            case "interface":
            default:
                baseFactor = 2.0;
        }

        // 响应时间影响（响应慢则收紧，快则放宽）
        double rtFactor;
        if (avgRt < 100) {
            rtFactor = 1.3;
        } else if (avgRt < 300) {
            rtFactor = 1.0;
        } else if (avgRt < 500) {
            rtFactor = 0.8;
        } else {
            rtFactor = 0.5;
        }

        // 异常率影响（高异常直接收紧）
        double errorFactor;
        if (errorRate > 0.2) {
            // 异常率极高，直接锁死
            return 2;
        } else if (errorRate > 0.05) {
            errorFactor = 0.5;
        } else {
            errorFactor = 1.0;
        }

        double threshold = qps * baseFactor * rtFactor * errorFactor;
        return Math.max(2, threshold);
    }

    public void start() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                List<FlowRule> rules = new ArrayList<>();
                Map<String, Map<String, ResourceStat>> allStats = ResourceStatManager.getAllStats();

                // 先更新参数热点
                ParamLimitManager.refreshHotParams(50); // 只保留Top 50参数为热点
                ParamLimitManager.resetFreq();

                for (String type : limitTypes) {
                    Map<String, ResourceStat> statMap = allStats.get(type);
                    if (statMap == null) continue;
                    for (Map.Entry<String, ResourceStat> entry : statMap.entrySet()) {
                        String resource = entry.getKey();
                        ResourceStat stat = entry.getValue();
                        double threshold = computeThreshold(type, stat);

                        // 只对热点/黑名单参数推送参数级限流规则
                        if ("parameter".equals(type) && !ParamLimitManager.isHotParam(resource)) {
                            continue;
                        }

                        FlowRule rule = new FlowRule();
                        // 资源名带类型前缀区分
                        rule.setResource(type + ":" + resource);
                        rule.setGrade(1); // QPS
                        rule.setCount(threshold);

                        rules.add(rule);
                    }
                }
                // 附加更新时间戳
                long ts = System.currentTimeMillis();
                SentinelRuleZkManager.pushRules(rules, ts);
                System.out.println("[AutoFlowRuleManager] Pushed rules at " + ts + ": " + rules);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }
}
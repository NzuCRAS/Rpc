package com.example.rpc.metrics;

import com.example.rpc.stability.stat.ResourceStat;
import com.example.rpc.stability.stat.ResourceStatManager;

import java.util.Map;

public class StatMetricsReporter {
    public static void report(String type, String resource, ResourceStat stat) {
        MetricsCollector.resourceQps.labels(type, resource).set(stat.getQps(1000));
        MetricsCollector.resourceRt.labels(type, resource).set(stat.getAvgRt());
        MetricsCollector.resourceErrorRate.labels(type, resource).set(stat.getErrorRate());
    }

    public static void reportFlowBlock(String type, String resource) {
        MetricsCollector.flowBlockCounter.labels(type, resource).inc();
    }

    public static void reportDegradeTrigger(String type, String resource) {
        MetricsCollector.degradeCounter.labels(type, resource).inc();
    }

    public static void reportSystemProtectTrigger(String type, String resource) {
        MetricsCollector.systemProtectCounter.labels(type, resource).inc();
    }

    public static void reportAlert(String type, String resource, String alertType) {
        MetricsCollector.alertCounter.labels(type, resource, alertType).inc();
    }

    public static void reportAll() {
        Map<String, Map<String, ResourceStat>> allStats = ResourceStatManager.getAllStats();
        for (String type : allStats.keySet()) {
            for (Map.Entry<String, ResourceStat> entry : allStats.get(type).entrySet()) {
                String resource = entry.getKey();
                ResourceStat stat = entry.getValue();
                StatMetricsReporter.report(type, resource, stat);
            }
        }
    }
}
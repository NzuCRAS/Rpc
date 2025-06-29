package com.example.rpc.stability.degrade;

import com.example.rpc.stability.stat.ResourceStat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自适应调优熔断参数，支持冷静期、恢复判据及多粒度配置
 */
public class AdaptiveDegradeTuner {
    // 保存自适应调优后的meta快照
    private static final Map<String, DegradeRuleMeta> adaptiveMetaMap = new ConcurrentHashMap<>();
    // 记录每个资源最后一次调优时间戳
    private static final Map<String, Long> lastTuneTimeMap = new ConcurrentHashMap<>();
    // 记录每个资源连续健康轮数
    private static final Map<String, Integer> healthyRoundsMap = new ConcurrentHashMap<>();

    /**
     * 调优入口：根据统计数据&当前meta&调优配置，返回自适应后的meta
     */
    public static DegradeRuleMeta tune(String resourceKey, ResourceStat stat, DegradeRuleMeta baseMeta) {
        AdaptiveTuneConfig config = AdaptiveDegradeConfigManager.getConfig(resourceKey);
        if (config == null || !config.adaptiveEnabled) return baseMeta; // 未开启自适应

        long now = System.currentTimeMillis();
        long lastTune = lastTuneTimeMap.getOrDefault(resourceKey, 0L);
        if (now - lastTune < config.cooldownSeconds * 1000L) {
            // 冷静期内不变
            return adaptiveMetaMap.getOrDefault(resourceKey, baseMeta);
        }

        DegradeRuleMeta meta = cloneMeta(baseMeta);

        boolean tuned = false;
        boolean healthy = false;

        // 判断异常率型
        if (meta.type == DegradeType.EXCEPTION_RATIO) {
            double errRate = stat.getErrorRate();
            // 异常率高，收紧
            if (errRate > meta.exceptionRatio + 0.05) {
                meta.exceptionRatio = Math.max(errRate * 0.9, config.minExceptionRatio);
                meta.timeWindow = Math.min(meta.timeWindow + 5, config.maxTimeWindow);
                tuned = true;
                healthyRoundsMap.put(resourceKey, 0);
            } else if (errRate < config.minExceptionRatio || (errRate < meta.exceptionRatio - 0.05 && errRate < 0.01)) {
                // 统计为健康
                healthy = true;
            }
        }
        // RT型
        if (meta.type == DegradeType.RT) {
            double avgRt = stat.getAvgRt();
            if (avgRt > meta.rtThreshold * 1.2) {
                meta.rtThreshold = Math.max(avgRt * 0.9, config.minRT);
                meta.timeWindow = Math.min(meta.timeWindow + 5, config.maxTimeWindow);
                tuned = true;
                healthyRoundsMap.put(resourceKey, 0);
            } else if (avgRt < config.minRT || avgRt < meta.rtThreshold * 0.8) {
                healthy = true;
            }
        }
        // ... 可扩展EXCEPTION_COUNT等类型

        // 恢复判据：连续N轮健康
        if (healthy && !tuned) {
            int rounds = healthyRoundsMap.getOrDefault(resourceKey, 0) + 1;
            healthyRoundsMap.put(resourceKey, rounds);
            if (rounds >= config.recoveryRounds) {
                // 放宽参数
                // 这里简单示例：回归到baseMeta配置
                meta = cloneMeta(baseMeta);
                tuned = true;
                healthyRoundsMap.put(resourceKey, 0);
            }
        }

        if (tuned) {
            lastTuneTimeMap.put(resourceKey, now);
            adaptiveMetaMap.put(resourceKey, meta);
            System.out.println("[AdaptiveDegradeTuner] " + resourceKey + " tuned: " + meta);
        }
        return meta;
    }

    private static DegradeRuleMeta cloneMeta(DegradeRuleMeta meta) {
        DegradeRuleMeta m = new DegradeRuleMeta();
        m.exceptionRatio = meta.exceptionRatio;
        m.minRequestAmount = meta.minRequestAmount;
        m.statIntervalMs = meta.statIntervalMs;
        m.timeWindow = meta.timeWindow;
        m.rtThreshold = meta.rtThreshold;
        m.exceptionCount = meta.exceptionCount;
        m.type = meta.type;
        m.resourceKey = meta.resourceKey;
        return m;
    }

    public static DegradeRuleMeta getAdaptiveMeta(String resourceKey) {
        return adaptiveMetaMap.get(resourceKey);
    }
}
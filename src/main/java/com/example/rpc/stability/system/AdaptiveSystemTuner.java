package com.example.rpc.stability.system;

import com.example.rpc.stability.stat.ResourceStat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统保护自适应调优，支持冷静期、恢复判据及多粒度配置
 */
public class AdaptiveSystemTuner {
    // 保存自适应调优后的meta快照
    private static final Map<String, SystemRuleMeta> adaptiveMetaMap = new ConcurrentHashMap<>();
    // 记录每个资源最后一次调优时间戳
    private static final Map<String, Long> lastTuneTimeMap = new ConcurrentHashMap<>();
    // 记录每个资源连续健康轮数
    private static final Map<String, Integer> healthyRoundsMap = new ConcurrentHashMap<>();
    private static final long QPS_WINDOW_MS = 1000; // 1秒

    /**
     * 调优入口：系统指标及业务统计+当前meta+调优配置
     */
    public static SystemRuleMeta tune(String resourceKey, SystemStatSnapshot sysStat, ResourceStat stat, SystemRuleMeta baseMeta) {
        AdaptiveSystemTuneConfig config = AdaptiveSystemTuneConfigManager.getConfig(resourceKey);
        if (config == null || !config.adaptiveEnabled) return baseMeta;

        long now = System.currentTimeMillis();
        long lastTune = lastTuneTimeMap.getOrDefault(resourceKey, 0L);
        if (now - lastTune < config.cooldownSeconds * 1000L) {
            // 冷静期内不变
            return adaptiveMetaMap.getOrDefault(resourceKey, baseMeta);
        }

        SystemRuleMeta meta = cloneMeta(baseMeta);
        boolean tuned = false;
        boolean healthy = false;

        // 负载自适应
        if (sysStat.load > meta.highestSystemLoad + 1) {
            meta.highestSystemLoad = Math.max(sysStat.load * 0.95, config.minLoad);
            tuned = true;
            healthyRoundsMap.put(resourceKey, 0);
        } else if (sysStat.load < config.minLoad + 0.5) {
            healthy = true;
        }

        // CPU自适应
        if (sysStat.cpuUsage > meta.highestCpuUsage + 0.05) {
            meta.highestCpuUsage = Math.max(sysStat.cpuUsage * 0.95, config.minCpu);
            tuned = true;
            healthyRoundsMap.put(resourceKey, 0);
        } else if (sysStat.cpuUsage < config.minCpu + 0.05) {
            healthy = true;
        }

        // QPS自适应
        if (stat.getQps(QPS_WINDOW_MS) > meta.maxQps * 1.2) {
            meta.maxQps = (long) Math.min(stat.getQps(QPS_WINDOW_MS) * 0.9, config.maxQps);
            tuned = true;
            healthyRoundsMap.put(resourceKey, 0);
        } else if (stat.getQps(QPS_WINDOW_MS) < meta.maxQps * 0.8) {
            healthy = true;
        }

        // 线程池自适应
        if (sysStat.threadCount > meta.maxThread * 1.2) {
            meta.maxThread = (long) Math.min(sysStat.threadCount * 0.9, config.maxThread);
            tuned = true;
            healthyRoundsMap.put(resourceKey, 0);
        } else if (sysStat.threadCount < meta.maxThread * 0.8) {
            healthy = true;
        }

        // 平均RT自适应
        if (stat.getAvgRt() > meta.maxAvgRt * 1.2) {
            meta.maxAvgRt = (long) Math.min(stat.getAvgRt() * 0.9, config.maxAvgRt);
            tuned = true;
            healthyRoundsMap.put(resourceKey, 0);
        } else if (stat.getAvgRt() < meta.maxAvgRt * 0.8) {
            healthy = true;
        }

        // 恢复判据
        if (healthy && !tuned) {
            int rounds = healthyRoundsMap.getOrDefault(resourceKey, 0) + 1;
            healthyRoundsMap.put(resourceKey, rounds);
            if (rounds >= config.recoveryRounds) {
                meta = cloneMeta(baseMeta);
                tuned = true;
                healthyRoundsMap.put(resourceKey, 0);
            }
        }

        if (tuned) {
            lastTuneTimeMap.put(resourceKey, now);
            adaptiveMetaMap.put(resourceKey, meta);
            System.out.println("[AdaptiveSystemTuner] " + resourceKey + " tuned: " + meta);
        }
        return meta;
    }

    private static SystemRuleMeta cloneMeta(SystemRuleMeta meta) {
        SystemRuleMeta m = new SystemRuleMeta();
        m.highestSystemLoad = meta.highestSystemLoad;
        m.highestCpuUsage = meta.highestCpuUsage;
        m.maxQps = meta.maxQps;
        m.maxThread = meta.maxThread;
        m.maxAvgRt = meta.maxAvgRt;
        m.resourceKey = meta.resourceKey;
        return m;
    }

    public static SystemRuleMeta getAdaptiveMeta(String resourceKey) {
        return adaptiveMetaMap.get(resourceKey);
    }
}
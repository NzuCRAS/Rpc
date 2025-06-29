package com.example.rpc.stability.stat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理所有资源粒度（接口/IP/用户等）的统计对象。
 * 支持多维度限流统计。
 */
public class ResourceStatManager {
    // type: interface/ip/user/param -> (value -> ResourceStat)
    private static final Map<String, Map<String, ResourceStat>> statMap = new ConcurrentHashMap<>();
    private static final long WINDOW_LENGTH_MS = 60_000; // 1分钟窗口

    /**
     * 埋点方法。每次RPC调用后，对每个粒度都要调用一次。
     * @param type 粒度类型（如"interface"/"ip"/"user"等）
     * @param value 粒度值（如接口名、IP、用户名等）
     * @param rt 本次调用耗时
     * @param success 是否成功
     * @param blocked 是否被限流
     */
    public static void record(String type, String value, long rt, boolean success, boolean blocked) {
        long now = System.currentTimeMillis();
        Map<String, ResourceStat> typeMap = statMap.computeIfAbsent(type, k -> new ConcurrentHashMap<>());
        typeMap.compute(value, (k, v) -> {
            if (v == null || now - v.getWindowStartTime() >= WINDOW_LENGTH_MS) {
                // 新窗口或首次创建
                v = new ResourceStat(value, now);
            }
            v.recordCall(rt, success, blocked);
            return v;
        });
    }

    /** 获取所有粒度类型的统计 */
    public static Map<String, Map<String, ResourceStat>> getAllStats() {
        return statMap;
    }

    /** 获取某个粒度类型的所有统计 */
    public static Map<String, ResourceStat> getStatsByType(String type) {
        return statMap.getOrDefault(type, new ConcurrentHashMap<>());
    }
}
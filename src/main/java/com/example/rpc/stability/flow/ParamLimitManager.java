package com.example.rpc.stability.flow;

import org.apache.commons.collections4.map.LRUMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ParamLimitManager {
    // 维护最近热度的参数，容量可配置
    private static final int HOT_PARAM_CAPACITY = 500;
    private static final LRUMap<String, AtomicInteger> paramFreqMap = new LRUMap<>(HOT_PARAM_CAPACITY);

    // 热点参数集合
    private static final Set<String> hotParams = ConcurrentHashMap.newKeySet();
    // 白名单/黑名单（可后续做成可热更配置）
    private static final Set<String> paramWhiteList = Set.of("VIP_ID1", "VIP_ID2");
    private static final Set<String> paramBlackList = Set.of("BLACK_ID1", "BLACK_ID2");

    /** 调用时记录参数访问频次 */
    public static void recordParam(String param) {
        if (param == null) return;
        if (isWhiteParam(param)) return; // 白名单不用统计
        synchronized (paramFreqMap) {
            paramFreqMap.computeIfAbsent(param, k -> new AtomicInteger()).incrementAndGet();
        }
    }

    /** 周期性选出TopN高频参数为热点参数 */
    public static void refreshHotParams(int topN) {
        List<Map.Entry<String, AtomicInteger>> sorted;
        synchronized (paramFreqMap) {
            sorted = new ArrayList<>(paramFreqMap.entrySet());
        }
        sorted.sort((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()));
        hotParams.clear();
        for (int i = 0; i < Math.min(topN, sorted.size()); i++) {
            hotParams.add(sorted.get(i).getKey());
        }
        hotParams.addAll(paramBlackList); // 黑名单参数永远热点
    }

    public static boolean isWhiteParam(String param) {
        return paramWhiteList.contains(param);
    }

    public static boolean isBlackParam(String param) {
        return paramBlackList.contains(param);
    }

    public static boolean isHotParam(String param) {
        return hotParams.contains(param) || isBlackParam(param);
    }

    public static Set<String> getCurrentHotParams() {
        return hotParams;
    }

    /** 可定期重置统计窗口 */
    public static void resetFreq() {
        synchronized (paramFreqMap) {
            paramFreqMap.clear();
        }
    }
}
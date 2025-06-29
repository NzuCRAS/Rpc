package com.example.rpc.stability.stat;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 统计某一资源、IP、用户等单一粒度的一段时间内的请求数据
 */
public class ResourceStat {
    private final String key; // 资源唯一标识（如接口名、IP等）
    private final AtomicLong totalCount = new AtomicLong();
    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong exceptionCount = new AtomicLong();
    private final AtomicLong totalRt = new AtomicLong();
    private final AtomicLong blockCount = new AtomicLong();
    private final AtomicLong maxRt = new AtomicLong();
    private final AtomicLong minRt = new AtomicLong(Long.MAX_VALUE);

    private volatile long windowStartTime; // 统计窗口起点

    public ResourceStat(String key, long windowStartTime) {
        this.key = key;
        this.windowStartTime = windowStartTime;
    }

    public void recordCall(long rt, boolean success, boolean blocked) {
        totalCount.incrementAndGet();
        totalRt.addAndGet(rt);
        if (blocked) blockCount.incrementAndGet();
        if (success) successCount.incrementAndGet();
        else exceptionCount.incrementAndGet();
        maxRt.updateAndGet(prev -> Math.max(prev, rt));
        minRt.updateAndGet(prev -> Math.min(prev, rt));
    }

    public double getQps(long windowMs) {
        return windowMs > 0 ? (totalCount.get() * 1000.0 / windowMs) : 0;
    }
    public double getAvgRt() {
        long t = totalCount.get();
        return t > 0 ? (double) totalRt.get() / t : 0;
    }
    public double getErrorRate() {
        long t = totalCount.get();
        return t > 0 ? (double) exceptionCount.get() / t : 0;
    }
    public long getTotalCount() { return totalCount.get(); }
    public long getExceptionCount() { return exceptionCount.get(); }
    public long getBlockCount() { return blockCount.get(); }
    public long getMaxRt() { return maxRt.get(); }
    public long getMinRt() { return minRt.get() == Long.MAX_VALUE ? 0 : minRt.get(); }

    public String getKey() { return key; }
    public long getWindowStartTime() { return windowStartTime; }
    public void setWindowStartTime(long ts) { this.windowStartTime = ts; }

    // 重置统计（供滑动/定长窗口使用）
    public void reset(long newWindowStartTime) {
        totalCount.set(0);
        successCount.set(0);
        exceptionCount.set(0);
        totalRt.set(0);
        blockCount.set(0);
        maxRt.set(0);
        minRt.set(Long.MAX_VALUE);
        windowStartTime = newWindowStartTime;
    }
}
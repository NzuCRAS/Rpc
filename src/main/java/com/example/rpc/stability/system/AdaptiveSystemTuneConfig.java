package com.example.rpc.stability.system;

/**
 * 系统保护自适应调优参数，可多粒度配置，支持热更
 */
public class AdaptiveSystemTuneConfig {
    public boolean adaptiveEnabled = true;
    public double minLoad = 2.0;
    public double maxLoad = 16.0;
    public double minCpu = 0.1;
    public double maxCpu = 0.99;
    public long minQps = 100;
    public long maxQps = 20000;
    public long minThread = 50;
    public long maxThread = 5000;
    public double minAvgRt = 100;
    public double maxAvgRt = 10000;
    public int cooldownSeconds = 120;     // 冷静期
    public int recoveryRounds = 3;        // 连续健康次数才逐步放宽
}
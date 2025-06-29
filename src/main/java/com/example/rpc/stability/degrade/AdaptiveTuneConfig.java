package com.example.rpc.stability.degrade;

public class AdaptiveTuneConfig {
    public boolean adaptiveEnabled = true;
    public double minExceptionRatio = 0.05;
    public double maxExceptionRatio = 0.4;
    public double minRT = 500;
    public double maxRT = 3000;
    public int minTimeWindow = 5;
    public int maxTimeWindow = 60;
    public int cooldownSeconds = 120;    // 冷静期，单位秒
    public int recoveryRounds = 3;       // 连续N轮健康才恢复参数
}
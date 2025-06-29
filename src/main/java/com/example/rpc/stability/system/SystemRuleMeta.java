package com.example.rpc.stability.system;

public class SystemRuleMeta {
    public String resourceKey;
    public double highestSystemLoad = 8.0;
    public double highestCpuUsage = 0.9;
    public long maxQps = 5000;
    public long maxThread = 1000;
    public double maxAvgRt = 1000;
}
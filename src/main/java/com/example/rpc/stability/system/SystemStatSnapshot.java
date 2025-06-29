package com.example.rpc.stability.system;

/**
 * 系统指标采集快照
 */
public class SystemStatSnapshot {
    public double load;
    public double cpuUsage;
    public long threadCount;

    public SystemStatSnapshot(double load, double cpuUsage, long threadCount) {
        this.load = load;
        this.cpuUsage = cpuUsage;
        this.threadCount = threadCount;
    }
}
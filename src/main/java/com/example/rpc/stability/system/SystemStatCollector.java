package com.example.rpc.stability.system;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import com.sun.management.OperatingSystemMXBean;

public class SystemStatCollector {
    private static final OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private static final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    public static SystemStatSnapshot collect() {
        double load = osBean.getSystemLoadAverage();
        double cpu = osBean.getSystemCpuLoad();
        long threadCount = threadMXBean.getThreadCount();
        return new SystemStatSnapshot(load, cpu, threadCount);
    }
}
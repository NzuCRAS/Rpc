package com.example.rpc.stability.degrade;


/**
* 单个熔断策略的配置元数据
 */
public class DegradeRuleMeta {
    public double exceptionRatio = 0.2; // 默认20%
    public int minRequestAmount = 30;
    public int statIntervalMs = 60000;
    public int timeWindow = 10;
    public double rtThreshold = 1000; // ms
    public int exceptionCount = 10;
    public DegradeType type = DegradeType.EXCEPTION_RATIO;

    // 支持扩展：不同接口/粒度可有不同meta
    public String resourceKey; // interface:xxx  ip:xxx  parameter:xxx
}
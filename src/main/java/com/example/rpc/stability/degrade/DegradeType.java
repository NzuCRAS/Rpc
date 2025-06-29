package com.example.rpc.stability.degrade;

public enum DegradeType {
    EXCEPTION_RATIO,    // 异常比例熔断
    EXCEPTION_COUNT,    // 异常数熔断
    RT                  // 响应时间熔断
}
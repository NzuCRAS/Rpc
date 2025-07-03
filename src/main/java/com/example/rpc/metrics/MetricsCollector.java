package com.example.rpc.metrics;


import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.HTTPServer;

public class MetricsCollector {
    // QPS/RT/异常率等
    public static final Gauge resourceQps = Gauge.build()
            .name("rpc_resource_qps")
            .help("QPS for each resource").labelNames("type", "resource").register();

    public static final Gauge resourceRt = Gauge.build()
            .name("rpc_resource_rt")
            .help("RT(ms) for each resource").labelNames("type", "resource").register();

    public static final Gauge resourceErrorRate = Gauge.build()
            .name("rpc_resource_error_rate")
            .help("Error Rate for each resource").labelNames("type", "resource").register();

    // 触发事件统计
    public static final Counter flowBlockCounter = Counter.build()
            .name("rpc_flow_block_total")
            .help("Total blocks for each resource").labelNames("type", "resource").register();

    public static final Counter degradeCounter = Counter.build()
            .name("rpc_degrade_trigger_total")
            .help("Total degrade triggers for each resource").labelNames("type", "resource").register();

    public static final Counter systemProtectCounter = Counter.build()
            .name("rpc_system_protect_trigger_total")
            .help("Total system protection triggers for each resource").labelNames("type", "resource").register();

    // 告警事件
    public static final Counter alertCounter = Counter.build()
            .name("rpc_alert_total")
            .help("Total alerts for each resource/alertType").labelNames("type", "resource", "alertType").register();

    // 启动Prometheus HTTP端点
    static {
        try {
            new HTTPServer(9095);
            System.out.println("HTTP Server Prometheus started");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
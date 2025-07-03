package com.example.rpc.metrics;

public class AlertManager {
    public static void sendAlert(String type, String resource, String alertType, String msg) {
        // 告警埋点
        StatMetricsReporter.reportAlert(type, resource, alertType);

        // 可对接钉钉/邮件/飞书/短信等
        // 实例代码略，可用HttpClient调用Webhook
        System.out.println("[ALERT] ["+type+"] ["+resource+"] ["+alertType+"]: " + msg);
    }
}
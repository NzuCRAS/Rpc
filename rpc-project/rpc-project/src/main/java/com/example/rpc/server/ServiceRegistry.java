package com.example.rpc.server;

import java.util.HashMap;
import java.util.Map;

public class ServiceRegistry {
    private final Map<String, Object> services = new HashMap<>();
    // 服务端注册服务,根据方法名调用具体的服务实现

    public void register(String serviceName, Object service) {
        services.put(serviceName, service);
    }

    public Object getService(String serviceName) {
        return services.get(serviceName);
    }
}

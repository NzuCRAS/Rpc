package com.example.rpc.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalServiceRegistry {
    private static final LocalServiceRegistry instance = new LocalServiceRegistry();

    private final Map<String, Object> services = new ConcurrentHashMap<String, Object>();
    // ConcurrentHashMap 是在多线程状态下也能够正常运行的HashMap

    public static LocalServiceRegistry getInstance() {
        return instance;
    }

    public void register(String serviceName, Object service) {
        services.put(serviceName, service);
        System.out.println("Registered service: " + serviceName);
    }

    public Object getService(String serviceName) {
        return services.get(serviceName);
    }
}

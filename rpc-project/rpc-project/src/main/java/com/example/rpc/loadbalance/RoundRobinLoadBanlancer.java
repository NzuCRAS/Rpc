package com.example.rpc.loadbalance;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// 轮询
public class RoundRobinLoadBanlancer implements LoadBalancer {
    private final AtomicInteger index = new AtomicInteger(0); // 原子整形

    @Override
    public String select(List<String> serviceAddresses) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            return null;
        }
        int position = Math.abs(index.getAndIncrement() % serviceAddresses.size());
        return serviceAddresses.get(position);
    }
}

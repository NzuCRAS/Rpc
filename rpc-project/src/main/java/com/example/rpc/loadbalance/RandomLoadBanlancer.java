package com.example.rpc.loadbalance;

import java.util.List;
import java.util.Random;

// 随机选取
public class RandomLoadBanlancer implements LoadBalancer {
    private final Random random = new Random();

    @Override
    public String select(List<String> serviceAddresses) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            return null;
        }
        int position = random.nextInt(serviceAddresses.size());
        return serviceAddresses.get(position);
    }
}

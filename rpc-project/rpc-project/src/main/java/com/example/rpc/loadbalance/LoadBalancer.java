package com.example.rpc.loadbalance;

import com.example.rpc.protocol.RpcMessage;

import java.util.List;

// 抽象负载均衡接口
public interface LoadBalancer {
    String select(List<String> serviceAddresses);
}

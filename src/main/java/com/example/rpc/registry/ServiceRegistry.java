package com.example.rpc.registry;

public interface ServiceRegistry {
    void register(String serviceName,String serviceAddress) throws Exception;
    // serviceAddress 是设置在 ZooKeeper中的地址
    void close() throws Exception;
}

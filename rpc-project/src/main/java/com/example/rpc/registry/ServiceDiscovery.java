package com.example.rpc.registry;

public interface ServiceDiscovery {
    String discover(String serviceName) throws Exception;
    void close() throws Exception;
}

package com.example.rpc.registry;

import java.util.List;

public interface ServiceDiscovery {
    String discover(String serviceName) throws Exception;
    void close() throws Exception;
    public List<String> getService(String serviceName) throws Exception;
}

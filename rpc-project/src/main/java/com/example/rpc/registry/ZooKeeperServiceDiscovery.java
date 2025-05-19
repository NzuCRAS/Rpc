package com.example.rpc.registry;

import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class ZooKeeperServiceDiscovery {
    private final ZooKeeper zooKeeper;

    public ZooKeeperServiceDiscovery(String connectString) throws Exception {
        // 连接到 ZooKeeper
        this.zooKeeper = new ZooKeeper(connectString, 5000, event -> {
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                System.out.println("Connected to ZooKeeper Successfully");
            }
        });
    }

    public String discover(String serviceName) throws Exception {
        // 获取服务节点
        String servicePath = "/service/" + serviceName;
        if (zooKeeper.exists(servicePath, false) == null) {
            throw new Exception("Service " + serviceName + " not found");
        }

        // 获取所有子节点
        List<String> children = zooKeeper.getChildren(servicePath, false);
        if (children.isEmpty()) {
            throw new Exception("Service instance" + serviceName + " not found");
        }

        // 随机选择一个服务实例
        String instancePath = servicePath + "/" + children.get(new Random().nextInt(children.size()));
        byte[] data = zooKeeper.getData(instancePath, false, null);
        return new String(data, StandardCharsets.UTF_8);
    }

    // 获取服务的所有实例地址
    public List<String> getService(String serviceName) throws Exception {
        // 获取服务节点路径
        String servicePath = "/service/" + serviceName;
        if (zooKeeper.exists(servicePath, false) == null) {
            throw new Exception("Service -" + serviceName + "- not found");
        }

        // 获取所有子节点 (服务实例)
        List<String> children = zooKeeper.getChildren(servicePath, false);
        return children.stream()
                .map(child -> {
                    try {
                        // 获取子节点
                        String instancePath = servicePath + "/" + child;
                        // 将数据转换成字节流
                        byte[] data = zooKeeper.getData(instancePath, false, null);
                        return new String(data, StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        throw new RuntimeException("get service instance failed" + child, e);
                    }
                })
                .collect(Collectors.toList()); // 将流中的元素转换成不同类型的结果
    }

    public void close() throws InterruptedException {
        zooKeeper.close();
    }
}

package com.example.rpc.registry;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;

public class ZooKeeperServiceRegistry implements ServiceRegistry {
    private static final int SESSION_TIMEOUT = 5000; // 会话超时时间
    private final ZooKeeper zooKeeper;

    // connect string 是以逗号分隔的主机:端口号列表
    public ZooKeeperServiceRegistry(String connectString) throws Exception {
        this.zooKeeper = new ZooKeeper(connectString, SESSION_TIMEOUT, event -> {
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                System.out.println("Connected to ZooKeeper");
            }
        });
    }

    public void register(String serviceName, String serviceAddress) throws Exception {
        // 确保父节点 /services 存在
        String rootPath = "/services";
        if (zooKeeper.exists(rootPath, false) == null) {
            zooKeeper.create(rootPath, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.println("Created " + rootPath);
        }

        // 创建服务节点路径
        String servicePath = "/services/" + serviceName;
        if (zooKeeper.exists(servicePath, false) == null) {
            // 如果服务节点不存在 创建持久节点
            System.out.println("Creating " + servicePath + " for " + serviceName);
            zooKeeper.create(servicePath, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }

        // 在服务名节点下创建临时子节点
        String instancePath = servicePath + "/instance-";
        zooKeeper.create(instancePath, serviceAddress.getBytes(StandardCharsets.UTF_8),
                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

        System.out.println("Registered service: " + serviceName + " @ " + serviceAddress);
    }

    public void close() throws InterruptedException {
        zooKeeper.close();
    }
}
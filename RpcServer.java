package com.example.rpc.server;

import com.example.rpc.protocol.*;
import com.example.rpc.registry.ServiceDiscovery;
import com.example.rpc.registry.ServiceRegistry;
import com.example.rpc.registry.ZooKeeperServiceDiscovery;
import com.example.rpc.registry.ZooKeeperServiceRegistry;
import com.example.rpc.service.HelloServiceImpl;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public class RpcServer {
    private final int port; // 端口号
    private final ServiceRegistry serviceRegistry; // 实现注册服务 用这个给注册表管理可用的服务
    private final ServiceDiscovery serviceDiscovery; // 实例发现

    public RpcServer(int port, String zooKeeperAddress) throws Exception {
        this.port = port;
        this.serviceRegistry = new ZooKeeperServiceRegistry(zooKeeperAddress);
        this.serviceDiscovery = new ZooKeeperServiceDiscovery(zooKeeperAddress);
    }

    public void start() throws Exception {
        // 服务地址
        String serviceAddress = "127.0.0.1:" + port;

        // 可更换配置序列化/反序列化所用的协议
        final Serializer serializer = new JsonSerializer();

        // 注册服务到 ZooKeeper
        serviceRegistry.register("HelloService", serviceAddress);

        // 注册本地服务
        LocalServiceRegistry.getInstance().register("HelloService", new HelloServiceImpl());

        // 创建两个线程组
        EventLoopGroup bossGroup = new NioEventLoopGroup(); // 负责接收客户端连接
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // 处理实际的读写操作

        try {
            ServerBootstrap bootstrap = new ServerBootstrap(); // 创建一个ServerBootstrap负责配置服务端相关网络参数
            bootstrap.group(bossGroup, workerGroup) // 配置EventLoopGroup
                    .channel(NioServerSocketChannel.class) // 使用NIO通道
                    .option(ChannelOption.SO_BACKLOG, 128) // 设置TCP参数-请求连接的最大队列长度
                    .childOption(ChannelOption.SO_KEEPALIVE, true) // 保持连接
                    .childHandler(new ChannelInitializer<SocketChannel>() { //自定义处理器
                        @Override
                        // SocketChannel 连接到TCP网络套接字的通道 面向流连接
                        // 这里就是把SocketChannel 通过自定义方法进行操作 比如自定义处理器
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new RpcMessageDecoder(serializer, RpcMessage.class));
                            ch.pipeline().addLast(new RpcMessageEncoder(serializer));
                            ch.pipeline().addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS));
                            ch.pipeline().addLast(new RpcServerHandler(serviceDiscovery, serializer)); // 自定义处理器
                        }
                    });

            System.out.println("RPC Server started on port " + port);
            ChannelFuture future = bootstrap.bind(port).sync(); // 绑定端口并启动 ChannelFuture时Channel异步IO操作的结果
            future.channel().closeFuture().sync(); // 等待连接关闭
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            // 连接关闭后EventLoopGroup也关闭
        }
    }

    public void stop() throws Exception {
        serviceRegistry.close();
        serviceDiscovery.close();
        System.out.println("RPC Server stopped");
    }
}

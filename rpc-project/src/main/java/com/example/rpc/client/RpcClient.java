package com.example.rpc.client;

import com.example.rpc.registry.ZooKeeperServiceDiscovery;
import com.example.rpc.registry.ZooKeeperServiceRegistry;
import com.example.rpc.service.HelloService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RpcClient {
    private final ZooKeeperServiceDiscovery serviceDiscovery;
    private Channel channel;
    // 创建了一个异步线程,相当于监听这个事件

    public RpcClient(String zooKeeperHost) throws Exception {
        this.serviceDiscovery = new ZooKeeperServiceDiscovery(zooKeeperHost);
    }

    public void connect(String serviceName) throws Exception {
        // 从 ZooKeeper 发现服务
        String serviceAddress = serviceDiscovery.discover(serviceName);
        System.out.println("Connecting to " + serviceAddress);

        // 解析服务地址
        String[] addressParts = serviceAddress.split(":");
        String host = addressParts[0];
        int port = Integer.parseInt(addressParts[1]);

        // 启动 Netty 客户端
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // Bootstrap 是 Netty 的启动类
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new StringDecoder());
                            ch.pipeline().addLast(new StringEncoder());
                            ch.pipeline().addLast(new RpcClientHandler() {
                            }); // 客户端处理器
                        }
                    });

            ChannelFuture future = bootstrap.connect(host, port).sync(); // 连接服务端
            this.channel = future.channel();
            System.out.println("Connected to " + serviceAddress);
        } catch (Exception e) {
            group.shutdownGracefully();
            throw e;
        } finally {
            // 不关闭 group 因为客户端需要保持连接
        }
    }

    public void sendRequest(String request) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(request);
        } else {
            throw new IllegalStateException("Client not connected");
        }
    }

    public void close() throws Exception {
        serviceDiscovery.close();
        if (channel != null) {
            channel.close();
        }
        System.out.println("RPC Client stopped");
    }
}

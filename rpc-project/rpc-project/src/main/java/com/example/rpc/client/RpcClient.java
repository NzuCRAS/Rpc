package com.example.rpc.client;

import com.example.rpc.protocol.JsonSerializer;
import com.example.rpc.protocol.RpcMessage;
import com.example.rpc.protocol.Serializer;
import com.example.rpc.registry.ZooKeeperServiceDiscovery;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RpcClient {
    private final ZooKeeperServiceDiscovery serviceDiscovery;

    // 确定使用JsonSerializer作为客户端处理器的序列化协议
    Serializer serializer;

    // 创建了一个异步线程,相当于监听这个事件
    private Channel channel;

    //用于关联请求和响应(ConcurrentHashMap是可以多线程同时使用的HashMap)
    private final ConcurrentHashMap<String, CompletableFuture<RpcMessage>> pendingRequest = new ConcurrentHashMap<>();

    public RpcClient(String zooKeeperHost, Serializer serializer) throws Exception {
        this.serviceDiscovery = new ZooKeeperServiceDiscovery(zooKeeperHost);
        this.serializer = serializer;
    }

    // 连接客户端与服务端,最后创建channel保证双方的双向通信
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
                            // 15秒无写操作,触发心跳(在RpcClientHandler中处理心跳)
                            ch.pipeline().addLast(new IdleStateHandler(0, 15, 0, TimeUnit.SECONDS));
                            ch.pipeline().addLast(new RpcClientHandler(serializer));
                            // 客户端处理器
                        }
                    });

            // 此时future就是所有事件的处理结果,而处理顺序 若为入站事件,则从头到尾依次调用handler,反之为出站事件则从尾到头调用
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

    public RpcMessage sendRequest(RpcMessage request) throws Exception {
        String requestId = UUID.randomUUID().toString();
        request.setRequestId(requestId);

        // 绑定requestId和异步处理结果
        CompletableFuture<RpcMessage> future = new CompletableFuture<>();
        pendingRequest.put(requestId, future);

        // 确定消息传递(在channel中)使用的是什么解码/编码工具以及编码格式
        String requestJson = new String(serializer.serialize(request), StandardCharsets.UTF_8);
        channel.writeAndFlush(requestJson);

        // 等待响应(设置超时时间)
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 如果响应超时则从HashMap中移除requestId并报错
            pendingRequest.remove(requestId);
            throw new RuntimeException("Rpc Request timed out");
        }
    }

    // 由RpcClientHandler在收到响应消息时调用
    public void receiveRequest(RpcMessage response) {
        String requestId = response.getRequestId();
        CompletableFuture<RpcMessage> future = pendingRequest.remove(requestId);
        if (future != null) {
            future.complete(response);
        }
    }

    public void close() throws Exception {
        serviceDiscovery.close();

        // 关闭通道
        if (channel != null) {
            channel.close();
        }
        System.out.println("RPC Client stopped");
    }
}
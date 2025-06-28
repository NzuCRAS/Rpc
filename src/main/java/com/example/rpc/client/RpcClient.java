package com.example.rpc.client;

import com.example.rpc.protocol.*;
import com.example.rpc.registry.ZooKeeperServiceDiscovery;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RpcClient {
    private final ZooKeeperServiceDiscovery serviceDiscovery; // 服务发现
    Serializer serializer; // 确定使用JsonSerializer作为客户端处理器的序列化协议
    private Channel channel; // 创建了一个异步线程,相当于监听这个事件
    private final ConcurrentHashMap<String, CompletableFuture<RpcMessage>> pendingRequest = new ConcurrentHashMap<>(); //用于关联请求和响应(ConcurrentHashMap是可以多线程同时使用的HashMap)

    private String serviceName; // 服务名称
    private Bootstrap bootstrap; // Netty启动类
    private EventLoopGroup group; // 线程池
    private int maxRetries = 3; // 最大重新连接尝试数
    private int retryDelay = 3; // 重试间隔(s)

    public RpcClient(String zooKeeperHost, Serializer serializer) throws Exception {
        this.serviceDiscovery = new ZooKeeperServiceDiscovery(zooKeeperHost);
        this.serializer = serializer;
    }

    // 显式设置重连接策略
    public void setRetryPolicy(int maxRetries, int retryDelay) {
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
    }

    // 连接客户端与服务端,最后创建channel保证双方的双向通信
    public synchronized void connect(String serviceName) throws Exception {
        this.serviceName = serviceName;

        // 如果已经存在group,则先将其释放
        if (group != null) {
            group.shutdownGracefully();
            group = null;
        }

        // 从 ZooKeeper 发现服务
        String serviceAddress = serviceDiscovery.discover(serviceName);
        System.out.println("Connecting to " + serviceAddress);

        // 解析服务地址
        String[] addressParts = serviceAddress.split(":");
        String host = addressParts[0];
        int port = Integer.parseInt(addressParts[1]);

        // 启动 Netty 客户端
        group = new NioEventLoopGroup();
        bootstrap = new Bootstrap();

        // 绑定Handler与当前client,方便后续通过Handler重新连接
        RpcClientHandler handler = new RpcClientHandler(this);

        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 3秒无写操作,触发心跳(在RpcClientHandler中处理心跳)
                        ch.pipeline().addLast(new IdleStateHandler(0, 3, 0, TimeUnit.SECONDS));
                        ch.pipeline().addLast(new RpcMessageDecoder(serializer, RpcMessage.class));
                        ch.pipeline().addLast(new RpcMessageEncoder(serializer));
                        ch.pipeline().addLast(handler);
                        // 客户端处理器
                    }
                });
        try {// 此时future就是所有事件的处理结果,而处理顺序 若为入站事件,则从头到尾依次调用handler,反之为出站事件则从尾到头调用
            ChannelFuture future = bootstrap.connect(host, port).sync(); // 连接服务端
            this.channel = future.channel();
            System.out.println("Connected to " + serviceAddress);
        } catch (Exception e) {
            // group在finally中不关闭,需要重连
            throw e;
        }
    }

    public void reconnect(int attempts) {
        if (attempts > maxRetries) {
            System.err.println("Max retries reached. Giving up.");
            return;
        }

        // 用 EventLoop 异步调度重连
        int delay = retryDelay;
        if (channel != null && channel.eventLoop() != null) {
            channel.eventLoop().schedule(() -> {
                // 在指定延迟之后执行,也就是schedule - 计划
                try {
                    System.out.println("Reconnecting Attempt: " + attempts);
                    connect(serviceName);
                } catch (Exception e) {
                    System.err.println("Reconnect failed: " + e.getMessage());
                    // 递归尝试
                    reconnect(attempts + 1);
                }
            }, delay, TimeUnit.SECONDS);
        } else {
            // fallback:如果没有可用的eventLoop则直接使用线程重试
            new Thread(() -> {
                try {
                    Thread.sleep(delay * 1000L);
                    System.out.println("Reconnecting Attempt: " + attempts);
                    connect(serviceName);
                } catch (Exception e) {
                    System.err.println("Reconnect failed: " + e.getMessage());
                    reconnect(attempts + 1);
                }
            }).start();
        }
    }

    public RpcMessage sendRequestWithRetry(RpcMessage request, int maxRetries, long timeoutMillis) throws Exception {
        // 对request的发送增加了重试机制,最多重试maxRetries次,每次重试等待间隔为timeoutMillis(ms)
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            String requestId = UUID.randomUUID().toString();
            request.setRequestId(requestId);

            CompletableFuture<RpcMessage> future = new CompletableFuture<>();
            pendingRequest.put(requestId, future);

            channel.writeAndFlush(request);

            try {
                return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | RuntimeException e) {
                lastException = e;
                pendingRequest.remove(requestId);
                attempt++;
                if (attempt < maxRetries) {
                    System.out.println("Request timeout, retrying... attempt " + (attempt + 1));
                }
            }
        }
        throw new RuntimeException("Rpc Request failed after " + maxRetries + " attempts", lastException);
    }

    // 由RpcClientHandler在收到响应消息时调用
    public void receiveRequest(RpcMessage response) {
        String requestId = response.getRequestId();
        CompletableFuture<RpcMessage> future = pendingRequest.remove(requestId);
        if (future != null) {
            future.complete(response);
        }
    }

    public String getServiceName(){return serviceName;}

    public void close() throws Exception {
        serviceDiscovery.close();

        // 关闭通道
        if (channel != null) {
            channel.close();
        }
        // 关闭线程池
        if (group != null) {
            group.shutdownGracefully();
        }
        System.out.println("RPC Client stopped");
    }
}
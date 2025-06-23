package com.example.rpc.server;

import com.example.rpc.protocol.JsonSerializer;
import com.example.rpc.protocol.RpcMessage;
import com.example.rpc.protocol.RpcMessageDecoder;
import com.example.rpc.protocol.Serializer;
import com.example.rpc.registry.ServiceDiscovery;
import com.example.rpc.registry.ZooKeeperServiceDiscovery;
import com.example.rpc.registry.ZooKeeperServiceRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class RpcServerHandler extends SimpleChannelInboundHandler<RpcMessage> {
    private final ServiceDiscovery serviceDiscovery;
    private final Serializer serializer;

    public RpcServerHandler(ServiceDiscovery serviceDiscovery, Serializer serializer) {
        this.serviceDiscovery = serviceDiscovery;
        this.serializer = serializer;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        System.out.println("server receive request: " + msg);

        // 在RpcServer/Client中已经定义了
        RpcMessage request = (RpcMessage) msg;

        if (request.getType().equals("heartbeat")) {
            System.out.println("[Server Receive Heartbeat]");
            return;
        }

        // 模拟处理请求
        RpcMessage response = new RpcMessage();
        response.setType("response");

        try {
            // 从服务发现中获取服务实例
            List<String> serviceInstance = serviceDiscovery.getService(request.getServiceName());
            if (serviceInstance == null) {
                throw new RuntimeException("No service found for method: " + request.getServiceName());
            }

            // 假设第一个服务实例是本地的实现(因为服务端调用的是本地实例)
            Object service = LocalServiceRegistry.getInstance().getService(request.getServiceName());
            if (service == null) {
                throw new RuntimeException("No Local service found for method: " + request.getServiceName());
            }

            // 处理请求
            Method method = service.getClass().getMethod(request.getMethodName(), String.class);
            Object result = method.invoke(service, request.getParams());
            response.setResult(result);

            System.out.println("Server Response:" + result);
        } catch (Exception e) {
            response.setError(e.getMessage());
            System.out.println("Server Error:" + e.getMessage());
        }

        ctx.writeAndFlush(response); // 将结果响应给客户端
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // 关闭超时连接
            ctx.close();
        } else {
            System.out.println("Server IdleStateEvent");
            // 将事件继续传递给下一个Handler,是Handler链中的一种"事件回调"机制
            // 在自定义的pipeline责任链中每一个Handler都只处理自己关心的事件,而调用这个函数就是证明自己不关心当前事件,把当前的事件交给下一个Handler
            super.userEventTriggered(ctx, evt);
        }
    }
}
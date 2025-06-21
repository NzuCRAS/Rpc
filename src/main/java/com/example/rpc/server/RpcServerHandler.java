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

        // 模拟处理请求
        RpcMessage response = new RpcMessage();
        response.setType("response");

        try {
            // 从服务发现中获取服务实例
            List<String> serviceInstance = serviceDiscovery.getService(request.getMethodName());
            if (serviceInstance == null) {
                throw new RuntimeException("No service found for method: " + request.getMethodName());
            }

            // 假设第一个服务实例是本地的实现(因为服务端调用的是本地实例)
            Object service = LocalServiceRegistry.getInstance().getService(request.getMethodName());
            if (service == null) {
                throw new RuntimeException("No Local service found for method: " + request.getMethodName());
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

        // 将响应对象序列化为 JSON 字符串
        String responseJson = Arrays.toString(serializer.serialize(response));
        ctx.writeAndFlush(responseJson); // 将结果响应给客户端
    }
}
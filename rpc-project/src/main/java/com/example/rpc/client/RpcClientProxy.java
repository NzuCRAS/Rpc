package com.example.rpc.client;

import com.example.rpc.protocol.JsonSerializer;
import com.example.rpc.protocol.RpcMessage;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class RpcClientProxy {
    private final RpcClient rpcClient;

    public RpcClientProxy(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> serviceClass) {
        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class<?>[]{serviceClass},
                new RpcInvocationHandler(rpcClient, serviceClass)
        );
    }

    private static class RpcInvocationHandler implements InvocationHandler {
        // 实现动态代理中 代理实例的调用处理程序接口
        private final RpcClient rpcClient; // 代理的客户端
        private final Class<?> serviceClass; // 代理类

        public RpcInvocationHandler(RpcClient rpcClient, Class<?> serviceClass) {
            this.rpcClient = rpcClient;
            this.serviceClass = serviceClass;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 构造 RPC 请求
            RpcMessage request = new RpcMessage();
            request.setType("request");
            request.setMethodName(method.getName());
            request.setParams(args);

            // 序列化请求并发送
            String requestJson = JsonSerializer.serialize(request);
            rpcClient.sendRequest(requestJson);
            String responseJson = "123";

            // 反序列化响应
            RpcMessage response = JsonSerializer.deserialize(responseJson, RpcMessage.class);
            if (response.getError() != null) {
                throw new RuntimeException("RPC 调用失败:" + response.getError());
            }
            return response.getResult();
        }
    }
}

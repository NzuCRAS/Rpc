package com.example.rpc.client;

import com.example.rpc.fault.FailStrategy;
import com.example.rpc.fault.RetryFailStrategy;
import com.example.rpc.fault.RpcInvocation;
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
                new RpcInvocationHandler(rpcClient)
        );
    }

    private static class RpcInvocationHandler implements InvocationHandler {
        // 实现动态代理中 代理实例的调用处理程序接口
        private final RpcClient rpcClient; // 代理的客户端

        public RpcInvocationHandler(RpcClient rpcClient) {
            this.rpcClient = rpcClient;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 使用RpcInvocation封装
            RpcInvocation invocation = () -> {
                // 构造 RPC 请求
                RpcMessage request = new RpcMessage();
                request.setType("request");
                request.setMethodName(method.getName());
                request.setParams(args);

                // 通过代理的rpcClient将消息发送并返回response
                RpcMessage response = rpcClient.sendRequestWithRetry(request, 3, 2000);
                if (response.getError() != null) {
                    throw new RuntimeException("Rpc Call Failed" + response.getError());
                }
                return response.getResult();
            };

            // 使用FailStrategy
            FailStrategy failStrategy = new RetryFailStrategy(3, 500);
            return failStrategy.invoke(invocation);
        }
    }
}
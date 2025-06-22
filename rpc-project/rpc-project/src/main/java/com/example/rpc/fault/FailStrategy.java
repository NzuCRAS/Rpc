package com.example.rpc.fault;

// 预留容错机制接口(重试/快速失败)
public interface FailStrategy {
    // RpcInvocation 是自定义远程调用描述对象
    Object invoke(RpcInvocation invocation) throws Exception;
}

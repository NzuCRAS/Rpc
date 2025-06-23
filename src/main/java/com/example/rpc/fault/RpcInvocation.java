package com.example.rpc.fault;

@FunctionalInterface
public interface RpcInvocation {
    Object invoke() throws Exception;
}

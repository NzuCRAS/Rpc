package com.example.rpc.client;

import com.example.rpc.protocol.JsonSerializer;
import com.example.rpc.protocol.RpcMessage;
import com.example.rpc.protocol.Serializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.Arrays;

public class RpcClientHandler extends ChannelInboundHandlerAdapter {
    Serializer serializer = new JsonSerializer();
    public RpcClientHandler(Serializer serializer) {this.serializer = serializer;}

    @Override // WRITE
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Conneted to server and ready to send request");

/*        // 创建请求信息
        RpcMessage request = new RpcMessage();
        request.setType("request");
        request.setMethodName("GREETING");
        request.setParams(new Object[]{"HELLO WORLD"});

        // 发送请求
        ctx.writeAndFlush(request);*/
    }

    @Override // READ
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 将接收到的响应消息反序列化为 RpcMessage 对象
        RpcMessage response = (RpcMessage) msg;
        System.out.println("Client receive response" + response);
    }

    @Override // 处理心跳
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // 发送心跳包
            RpcMessage heartbeat = new RpcMessage();
            // 将RpcMessage类型定义为heartbeat方便判断
            heartbeat.setType("heartbeat");
            System.out.println("[Client Send Heartbeat]");
            ctx.writeAndFlush(heartbeat);
        } else {
            // 心跳超时,调用
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override // EXCEPTION
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close(); // 出现异常时关闭连接
    }
}
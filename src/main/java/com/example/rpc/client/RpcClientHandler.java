package com.example.rpc.client;

import com.example.rpc.protocol.JsonSerializer;
import com.example.rpc.protocol.RpcMessage;
import com.example.rpc.protocol.Serializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.Arrays;

public class RpcClientHandler extends ChannelInboundHandlerAdapter {
    private int missedHeartbeats = 0;
    private static final int MAX_MISSED = 3;

    private final RpcClient rpcClient;

    public RpcClientHandler(RpcClient rpcClient) {this.rpcClient = rpcClient;}

    @Override // WRITE
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        missedHeartbeats = 0;
        System.out.println("Conneted to server and ready to send request");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Channel is inactive");
        rpcClient.reconnect(1);
    }

    @Override // READ
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 将接收到的响应消息反序列化为 RpcMessage 对象
        RpcMessage response = (RpcMessage) msg;
        if ("heartbeat".equals(response.getType())) {
            missedHeartbeats = 0; // 重置心跳丢包计数
            return;
        }

        // 业务处理
        System.out.println("Client receive response" + response);
    }

    @Override // 处理心跳
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // 如果是IdleStateEvent,则由本处理器处理
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                // IdleEventHandler产生的 "一段时间没有读数据" 的时间,具体 "一段时间" 在配置pipeline的IdleEventHandler中设置
                missedHeartbeats++;
                if (missedHeartbeats >= MAX_MISSED) {
                    // 如果 "没有收到心跳" 的次数超过限制,则可以判断服务端断连,从而开启重连
                    System.out.println("[Client] Heartbeat lost " + missedHeartbeats + " heartbeats" );
                    rpcClient.reconnect(1);

                    // 重连失败就关闭channel
                    ctx.close();
                }
        } else {
            // 心跳超时,调用
            super.userEventTriggered(ctx, evt);
            }
        }
    }
}
package com.example.rpc.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class RpcMessageEncoder extends MessageToByteEncoder<Object> {
    private final Serializer serializer;

    public RpcMessageEncoder(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        byte[] data = serializer.serialize(msg);
        out.writeInt(data.length);
        out.writeBytes(data);
    }
}

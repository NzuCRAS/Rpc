package com.example.rpc.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class RpcMessageDecoder extends ByteToMessageDecoder {
    private final Serializer serializer;
    private final Class<?> genericClass; // 通用类

    public RpcMessageDecoder(Serializer serializer, Class<?> genericClass) {
        this.serializer = serializer;
        this.genericClass = genericClass;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int dataLength = in.readInt();
        byte[] data = new byte[dataLength];
        in.readBytes(data);
        Object obj = serializer.deserialize(data, genericClass);
        out.add(obj);
    }
}

package com.example.rpc.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSerializer implements Serializer {
    // 从字符串/流/文件解析JSON,并创建JAVA对象/对象图来表示已解析的JSON
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 序列化 将对象转化为JSON字符串
    @Override
    public byte[] serialize(Object obj) throws Exception {
        return objectMapper.writeValueAsBytes(obj);
    }

    // 反序列化 将JSON字符串转化为对象
    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) throws Exception {
        return objectMapper.readValue(data, clazz);
    }
}

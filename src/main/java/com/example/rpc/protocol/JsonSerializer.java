package com.example.rpc.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JsonSerializer implements Serializer {
    // 从字符串/流/文件解析JSON,并创建JAVA对象/对象图来表示已解析的JSON
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 序列化 将对象转化为JSON字符串
    @Override
    public byte[] serialize(Object obj) throws IOException {
        return objectMapper.writeValueAsBytes(obj);
    }

    // 反序列化 将JSON字符串转化为对象
    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) throws IOException {
        return objectMapper.readValue(data, clazz);
    }

    @Override
    public <T> List<T> listDeserialize(byte[] bytes, Class<T> clazz) throws IOException {
        if (bytes == null || bytes.length == 0) return Collections.emptyList();
        return objectMapper.readValue(bytes,
                objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
    }

    @Override
    public <T> Map<String, T> mapDeserialize(byte[] bytes, Class<T> clazz) throws IOException {
        if (bytes == null || bytes.length == 0) return Collections.emptyMap();
        return objectMapper.readValue(bytes,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, clazz));
    }
}

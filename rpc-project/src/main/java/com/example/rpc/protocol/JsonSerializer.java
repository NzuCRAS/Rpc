package com.example.rpc.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSerializer {
    // 从字符串/流/文件解析JSON,并创建JAVA对象/对象图来表示已解析的JSON
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 序列化 将对象转化为JSON字符串
    public static String serialize(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }

    // 反序列化 将JSON字符串转化为对象
    public static <T> T deserialize(String json, Class<T> clazz) throws JsonProcessingException {
        return objectMapper.readValue(json, clazz);
    }
}

package com.example.rpc.protocol;

import java.util.List;
import java.util.Map;

public interface Serializer {
    // 支持多种序列化/反序列化协议

    byte[] serialize(Object obj) throws Exception;
    <T> T deserialize(byte[] bytes, Class<T> clazz) throws Exception;
    <T> List<T> listDeserialize(byte[] bytes, Class<T> clazz) throws Exception;
    <T> Map<String, T> mapDeserialize(byte[] bytes, Class<T> clazz) throws Exception;
}

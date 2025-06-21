package com.example.rpc.protocol;

import java.io.Serializable;

// Rpc消息类
public class RpcMessage implements Serializable {
    private String type; // 消息类型 (request / response)
    private String methodName; // 请求的方法名
    private Object[] params; // 参数
    private Object result;
    private String error; // 错误信息
    private String requestId; // UUID字符串

    public RpcMessage() {} // 构造器

    public RpcMessage(String type, String methodName, Object[] params, Object result, String error, String requestId) {
        this.type = type;
        this.methodName = methodName;
        this.params = params;
        this.result = result;
        this.error = error;
        this.requestId = requestId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Object[] getParams() {
        return params;
    }

    public void setParams(Object[] params) {
        this.params = params;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    @Override
    public String toString() {
        return  "RpcMessage{" +
                "type = '" + type + '\'' +
                ", methodName = '" + methodName + '\'' +
                ", parameters = " + (params == null ? "null" : params.length) +
                ", result = " + result +
                ", error = '" + error + '\'' +
                '}';
    }
}

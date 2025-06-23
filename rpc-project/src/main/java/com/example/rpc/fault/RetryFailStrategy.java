package com.example.rpc.fault;

public class RetryFailStrategy implements FailStrategy{
    private final int maxRetry; // 最多尝试次数
    private final long retryIntervalMs; // 等待尝试时间(ms)

    public RetryFailStrategy(int maxRetry, long retryIntervalMs) {
        this.maxRetry = maxRetry;
        this.retryIntervalMs = retryIntervalMs;
    }

    @Override
    public Object invoke(RpcInvocation invocation) throws Exception {
        int retryCount = 0;
        while (true) {
            try {
                return invocation.invoke();
            } catch (Exception e) {
                retryCount++;
                if (retryCount >= maxRetry) throw e;
                Thread.sleep(retryIntervalMs);
            }
        }
    }
}

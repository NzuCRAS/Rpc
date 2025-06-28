package com.example.rpc.server;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.example.rpc.protocol.RpcMessage;
import com.example.rpc.registry.ServiceDiscovery;
import com.example.rpc.stability.SentinelLimitManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class RpcServerHandler extends SimpleChannelInboundHandler<RpcMessage> {
    private final ServiceDiscovery serviceDiscovery;  // 服务发现
    private int missedHeartbeats = 0; // 丢失的心跳计数器
    private static final int MAX_MISSED = 3; // 最多的心跳丢失数

    public static class ThreadPoolConfig {
        private int corePoolSize = 4; // 核心线程(始终存在于池中,池中始终维护,处非设置了核心线程超时时间)数
        private int maxPoolSize = 8; // 最大线程数
        private int queueCapacity = 100; // 任务队列容量(阻塞队列)
        private long keepAliveTime = 60; // 线程空闲时间(空闲时间到达该程度则关闭线程,直至线程只剩核心线程)
        // getters and setters

        public ThreadPoolConfig(int corePoolSize, int maxPoolSize, int queueCapacity, long keepAliveTime) {
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.queueCapacity = queueCapacity;
            this.keepAliveTime = keepAliveTime;
        }

        public int getCorePoolSize() {return corePoolSize;}
        public int getMaxPoolSize() {return maxPoolSize;}
        public int getQueueCapacity() {return queueCapacity;}
        public long getKeepAliveTime() {return keepAliveTime;}

    }

    // 业务线程池
    // static 或 单例化
    // 合理配置线程池+队列容量+快拒绝 配合限流组件或降级兜底
    private static ThreadPoolExecutor businessThreadPool = new ThreadPoolExecutor(
            4, 8, 60, TimeUnit.SECONDS, // Unit一般指代单位
            new ArrayBlockingQueue<>(100),
            new ThreadPoolExecutor.AbortPolicy() // 队列满时直接拒绝
            // 此处直接拒绝可以保护系统不被等待队列等影响甚至拖垮,避免过载同时及时响应
            // 针对不同的服务端配置可以设置不同的配置
    );

    public static void configureThreadPool(ThreadPoolConfig config) {
        businessThreadPool = new ThreadPoolExecutor(
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getKeepAliveTime(), TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(config.getQueueCapacity()),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    // Sentinel规则初始化
    static {
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule();
        rule.setResource("rpc_server_resource");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(100); // 100 QPS
        rules.add(rule);
        FlowRuleManager.loadRules(rules);
    }

    public RpcServerHandler(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    private String getClientIp(ChannelHandlerContext ctx) {
        return ctx.channel().remoteAddress().toString();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        if ("heartbeat".equals(msg.getType())) {
            System.out.println("[Server Receive Heartbeat]");
            missedHeartbeats = 0;
            RpcMessage heartbeat = new RpcMessage();
            heartbeat.setType("heartbeat");
            ctx.writeAndFlush(heartbeat);
            return;
        }

        String resourceName = msg.getServiceName() + "_" + msg.getMethodName();
        String clientIp = getClientIp(ctx);
        Object[] params = msg.getParams();

        // 先IP限流
        if (!SentinelLimitManager.tryIpLimit(clientIp)) {
            RpcMessage busyResponse = new RpcMessage();
            busyResponse.setType("response");
            busyResponse.setRequestId(msg.getRequestId());
            busyResponse.setError("IP限流，请稍后再试");
            ctx.writeAndFlush(busyResponse);
            return;
        }

        // 参数级限流
        try (Entry entry = SphU.entry(resourceName)) {
            // 接口级限流
            try (Entry ifaceEntry = SentinelLimitManager.tryInterfaceEntry(resourceName)) {
                businessThreadPool.execute(() -> {
                    RpcMessage response = new RpcMessage();
                    response.setType("response");
                    response.setRequestId(msg.getRequestId());

                    try {
                        List<String> serviceInstance = serviceDiscovery.getService(msg.getServiceName());
                        if (serviceInstance == null) {
                            throw new RuntimeException("No service found for method: " + msg.getServiceName());
                        }
                        Object service = LocalServiceRegistry.getInstance().getService(msg.getServiceName());
                        if (service == null) {
                            throw new RuntimeException("No Local service found for method: " + msg.getServiceName());
                        }
                        Method method = service.getClass().getMethod(msg.getMethodName(), String.class);
                        Object result = method.invoke(service, msg.getParams());
                        response.setResult(result);
                        System.out.println("Server Response:" + result);
                    } catch (Exception e) {
                        response.setError(e.getMessage());
                        System.out.println("Server Error:" + e.getMessage());
                    }
                    ctx.writeAndFlush(response);
                });
            } catch (BlockException ex) {
                RpcMessage busyResponse = new RpcMessage();
                busyResponse.setType("response");
                busyResponse.setRequestId(msg.getRequestId());
                busyResponse.setError("接口级限流，请稍后再试");
                ctx.writeAndFlush(busyResponse);
            }
        } catch (BlockException ex) {
            RpcMessage busyResponse = new RpcMessage();
            busyResponse.setType("response");
            busyResponse.setRequestId(msg.getRequestId());
            busyResponse.setError("参数级限流，请稍后再试");
            ctx.writeAndFlush(busyResponse);
        } finally {
            ContextUtil.exit();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                System.out.println("[Server Miss Heartbeat]");
                missedHeartbeats++;
                if (missedHeartbeats >= MAX_MISSED) {
                    System.out.println("[Server] Heartbeat lost " + missedHeartbeats + " heartbeats");
                    ctx.close();
                }
            } else if (event.state() == IdleState.WRITER_IDLE) {
                RpcMessage heartbeat = new RpcMessage();
                heartbeat.setType("heartbeat");
                System.out.println("[Server Send Heartbeat]");
                ctx.writeAndFlush(heartbeat);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
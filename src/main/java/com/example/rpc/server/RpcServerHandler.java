package com.example.rpc.server;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.example.rpc.protocol.RpcMessage;
import com.example.rpc.registry.ServiceDiscovery;
import com.example.rpc.stability.flow.ParamLimitManager;
import com.example.rpc.stability.stat.ResourceStatManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class RpcServerHandler extends SimpleChannelInboundHandler<RpcMessage> {
    private final ServiceDiscovery serviceDiscovery;
    private int missedHeartbeats = 0;
    private static final int MAX_MISSED = 3;
    private static ThreadPoolExecutor businessThreadPool = new ThreadPoolExecutor(
            4, 8, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            new ThreadPoolExecutor.AbortPolicy()
    );

    public RpcServerHandler(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    private String getClientIp(ChannelHandlerContext ctx) {
        return ctx.channel().remoteAddress().toString();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        long startTime = System.currentTimeMillis();
        if ("heartbeat".equals(msg.getType())) {
            missedHeartbeats = 0;
            RpcMessage heartbeat = new RpcMessage();
            heartbeat.setType("heartbeat");
            ctx.writeAndFlush(heartbeat);
            return;
        }

        String resourceName = msg.getServiceName() + "_" + msg.getMethodName();
        String clientIp = getClientIp(ctx);

        // 1. IP级限流（推荐用 Sentinel 统一入口）
        String ipResource = "ip:" + clientIp;
        try (Entry ipEntry = SphU.entry(ipResource)) {
            // 2. 参数级限流（只对热点/黑名单参数，白名单不做限流）
            Object[] params = msg.getParams();
            List<Entry> paramEntries = new ArrayList<>();
            try {
                for (Object param : params) {
                    String paramStr = param.toString();
                    // 跳过白名单
                    if (ParamLimitManager.isWhiteParam(paramStr)) continue;
                    // 只对热点/黑名单参数限流
                    if (ParamLimitManager.isHotParam(paramStr)) {
                        paramEntries.add(SphU.entry("parameter:" + paramStr));
                    }
                }
                // 3. 接口级限流
                String interfaceResource = "interface:" + resourceName;
                try (Entry ifaceEntry = SphU.entry(interfaceResource)) {
                    businessThreadPool.execute(() -> {
                        RpcMessage response = new RpcMessage();
                        response.setType("response");
                        response.setRequestId(msg.getRequestId());
                        long rt = 0;
                        boolean success = false;
                        try {
                            List<String> serviceInstance = serviceDiscovery.getService(msg.getServiceName());
                            if (serviceInstance == null) throw new RuntimeException("No service found for method: " + msg.getServiceName());
                            Object service = LocalServiceRegistry.getInstance().getService(msg.getServiceName());
                            if (service == null) throw new RuntimeException("No Local service found for method: " + msg.getServiceName());
                            Method method = service.getClass().getMethod(msg.getMethodName(), String.class);
                            Object result = method.invoke(service, msg.getParams());
                            response.setResult(result);
                            success = true;
                        } catch (Exception e) {
                            response.setError(e.getMessage());
                        } finally {
                            rt = System.currentTimeMillis() - startTime;
                            // 埋点
                            ResourceStatManager.record("interface", resourceName, rt, success, false);
                            ResourceStatManager.record("ip", clientIp, rt, success, false);
                            for (Object param : params) {
                                String paramStr = param.toString();
                                ResourceStatManager.record("parameter", paramStr, rt, success, false);
                                // 统计参数热度
                                ParamLimitManager.recordParam(paramStr);
                            }
                        }
                        ctx.writeAndFlush(response);
                    });
                } catch (BlockException ex) {
                    // 接口级被限流
                    RpcMessage busyResponse = new RpcMessage();
                    busyResponse.setType("response");
                    busyResponse.setRequestId(msg.getRequestId());
                    busyResponse.setError("接口级限流，请稍后再试");
                    ctx.writeAndFlush(busyResponse);
                    ResourceStatManager.record("interface", resourceName, 0, false, true); // 埋点
                }
            } catch (BlockException ex) {
                // 参数级被限流
                RpcMessage busyResponse = new RpcMessage();
                busyResponse.setType("response");
                busyResponse.setRequestId(msg.getRequestId());
                busyResponse.setError("参数级限流，请稍后再试");
                ctx.writeAndFlush(busyResponse);
                for (Object param : params) {
                    String paramStr = param.toString();
                    ResourceStatManager.record("parameter", paramStr, 0, false, true);
                    // 统计参数热度
                    ParamLimitManager.recordParam(paramStr);
                }
            } finally {
                for (Entry entry : paramEntries) {
                    if (entry != null) entry.exit();
                }
            }
        } catch (BlockException ex) {
            // IP级被限流
            RpcMessage busyResponse = new RpcMessage();
            busyResponse.setType("response");
            busyResponse.setRequestId(msg.getRequestId());
            busyResponse.setError("IP限流，请稍后再试");
            ctx.writeAndFlush(busyResponse);
            ResourceStatManager.record("ip", clientIp, 0, false, true);
        } finally {
            ContextUtil.exit();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                missedHeartbeats++;
                if (missedHeartbeats >= MAX_MISSED) {
                    ctx.close();
                }
            } else if (event.state() == IdleState.WRITER_IDLE) {
                RpcMessage heartbeat = new RpcMessage();
                heartbeat.setType("heartbeat");
                ctx.writeAndFlush(heartbeat);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
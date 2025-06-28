# rpc开发笔记

### 6.20

#### 关于RpcClientHandler(客户端消息处理器)中的序列化工具部分设计

在开发时，我暂时使用JsonSerializer作为序列化的工具，同时定义了Serializer接口以备后续多种序列化拓展：

`

    public interface Serializer {
    // 支持多种序列化/反序列化协议
    byte[] serialize(Object obj) throws Exception;
    <T> T deserialize(byte[] bytes, Class<T> clazz) throws Exception;
    }

`

而在RpcClientHandler中，为了处理客户端的消息，需要将客户端的消息RpcMessage序列化(成字节流)之后再通过网络通道传输至服务端，然后在服务端反序列化成为具体的类RpcMessage，并进行处理

为了保证RpcClientHandler能够灵活地使用多种序列化工具，在类中面向Serializer接口编程，而在该类外部确定序列化工具并将实例传递给该类，然后用构造函数赋值，这样如果需要改变处理客户端消息时使用的序列化工具，就只需要在使用该类的时候确定就可以了，不需要在该类内部修改

---

### 6.21

#### 关于网络通信部分对接收/发送消息的处理

在开发时，为了实现网络数据和业务逻辑的解耦，使用Netty的pipeline作为责任链，将消息按照pipeline中规定的顺序处理，如下：

`

            bootstrap.group(group) // group为一个EventLoopGroup，用于接收连接并处理
                    .channel(NioSocketChannel.class) // 使用Nio通道
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new StringDecoder());
                            ch.pipeline().addLast(new StringEncoder());
                            ch.pipeline().addLast(new RpcClientHandler(serializer));
                            // 客户端处理器
                        }
                    });

`

首先使用bootstrap(Netty的启动类)启动Netty服务，然后使用NioSocketChannel(同步非阻塞通道)，最后设置pipeline的处理顺序：

收到请求时 -> 网络收到字节流 -> StringDecoder将字节流转化成RpcMessage -> 让RpcClientHandler执行业务处理并生成相应

返回请求时 -> RpcClientHandler生成RpcMessage响应对象 -> StringEncoder将响应对象编码成字节流 -> 通过网络发送到客户端

这样实现，就可以让业务逻辑和网络数据划清界限，使用什么处理方式与怎样接收网络数据解耦

#### 关于客户端RpcMessage消息的发送中同步等待的设计

`

    //用于关联请求和响应(ConcurrentHashMap是可以多线程同时使用的HashMap)
    private final ConcurrentHashMap<String, CompletableFuture<RpcMessage>> pendingRequest = new ConcurrentHashMap<>();

    public RpcMessage sendRequest(RpcMessage request) throws Exception {
        String requestId = UUID.randomUUID().toString();
        request.setRequestId(requestId);

        // 绑定requestId和异步处理结果
        CompletableFuture<RpcMessage> future = new CompletableFuture<>();
        pendingRequest.put(requestId, future);

        // 确定消息传递(在channel中)使用的是什么解码/编码工具以及编码格式
        String requestJson = new String(serializer.serialize(request), StandardCharsets.UTF_8);
        channel.writeAndFlush(requestJson);

        // 等待响应(设置超时时间)
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 如果响应超时则从HashMap中移除requestId并报错
            pendingRequest.remove(requestId);
            throw new RuntimeException("Rpc Request timed out");
        }
    }

    // 供RpcClientHandler使用,当接收到服务端的response时调用
    public void receiveRequest(RpcMessage response) {
        String requestId = response.getRequestId();
        // 通过pendingRequest比较request和response的requestId,如果相同则返回至future
        CompletableFuture<RpcMessage> future = pendingRequest.remove(requestId);
        if (future != null) {
            future.complete(response);
        }
    }

`

为每个request都设置了一串UUID作为标识,在这里用于维护request和response的对应关系,并通过ConcurrentHashMap关联request和response

在服务端处理完request并返回response后,根据最开始创建的ConcurrentHashMap比较request和response的UUID,如果相同则说明是对应的正确response,否则不予返回至future,客户端也就读取不到,直接超时处理

---

### 6.23

#### 关于网络通信心跳机制和超时重传的设计

在RpcServer中：

`

                        protected void initChannel(SocketChannel ch) {
                            // 接收客户端心跳信息
                            ch.pipeline().addLast(new IdleStateHandler(10, 0, 0, TimeUnit.SECONDS));
                            ch.pipeline().addLast(new RpcMessageDecoder(serializer, RpcMessage.class));
                            ch.pipeline().addLast(new RpcMessageEncoder(serializer));
                            ch.pipeline().addLast(new RpcServerHandler(serviceDiscovery, serializer)); // 自定义处理器
                        }

`

在RpcClient中：

`

                        protected void initChannel(SocketChannel ch) {
                            // 3秒无写操作,触发心跳(在RpcClientHandler中处理心跳)
                            ch.pipeline().addLast(new IdleStateHandler(0, 3, 0, TimeUnit.SECONDS));
                            ch.pipeline().addLast(new RpcMessageDecoder(serializer, RpcMessage.class));
                            ch.pipeline().addLast(new RpcMessageEncoder(serializer));
                            ch.pipeline().addLast(new RpcClientHandler(serializer));
                            // 客户端处理器
                        }

`

在pipeline责任链中增加一个用于处理心跳信息的处理器

在RpcClient中,每次操作后3秒客户端没有写操作,则向客户端发送一个心跳信息,详情为：

`

    @Override // 处理心跳
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // 发送心跳包
            RpcMessage heartbeat = new RpcMessage();
            // 将RpcMessage类型定义为heartbeat方便判断
            heartbeat.setType("heartbeat");
            System.out.println("[Client Send Heartbeat]");
            ctx.writeAndFlush(heartbeat);
        } else {
            // 心跳超时,调用
            super.userEventTriggered(ctx, evt);
        }
    }

`

而在RpcServer中,每次接收信息后10秒没有接收到客户端信息(包括心跳信息),则中断连接,详情为：

`

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // 关闭超时连接
            ctx.close();
        } else {
            System.out.println("Server IdleStateEvent");
            // 将事件继续传递给下一个Handler,是Handler链中的一种"事件回调"机制
            // 在自定义的pipeline责任链中每一个Handler都只处理自己关心的事件,而调用这个函数就是证明自己不关心当前事件,把当前的事件交给下一个Handler
            super.userEventTriggered(ctx, evt);
        }
    }

`

通过这些设计,可以方便服务端判断当前与客户端的链接是否有必要持续保持,有利于减少不必要的资源消耗和后续与心跳机制相关的如异常处理和监控报警功能的拓展

---

#### 关于心跳机制的更新与优化

在优化中,新增了客户端/服务端双方都能发送/接收心跳的机制,通过这一机制定时检查连接情况,并根据连接的情况断开连接：

客户端pipeline责任链配置

`

        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 3秒无写操作,触发心跳(在RpcClientHandler中处理心跳)
                        ch.pipeline().addLast(new IdleStateHandler(0, 3, 0, TimeUnit.SECONDS));
                        ch.pipeline().addLast(new RpcMessageDecoder(serializer, RpcMessage.class));
                        ch.pipeline().addLast(new RpcMessageEncoder(serializer));
                        ch.pipeline().addLast(handler);
                        // 客户端处理器
                    }
                });

`

服务端pipeline责任链配置

`

            bootstrap.group(bossGroup, workerGroup) // 配置EventLoopGroup
                    .channel(NioServerSocketChannel.class) // 使用NIO通道
                    .option(ChannelOption.SO_BACKLOG, 128) // 设置TCP参数-请求连接的最大队列长度
                    .childOption(ChannelOption.SO_KEEPALIVE, true) // 保持连接
                    .childHandler(new ChannelInitializer<SocketChannel>() { //自定义处理器
                        @Override
                        // SocketChannel 连接到TCP网络套接字的通道 面向流连接
                        // 这里就是把SocketChannel 通过自定义方法进行操作 比如自定义处理器
                        protected void initChannel(SocketChannel ch) {
                            // 接收客户端心跳信息
                            ch.pipeline().addLast(new IdleStateHandler(10, 0, 0, TimeUnit.SECONDS));
                            ch.pipeline().addLast(new RpcMessageDecoder(serializer, RpcMessage.class));
                            ch.pipeline().addLast(new RpcMessageEncoder(serializer));
                            ch.pipeline().addLast(new RpcServerHandler(serviceDiscovery)); // 自定义处理器
                        }
                    });

`

IdleStateHandler是一个Netty自带的触发器

在客户端触发器中
- 3秒没有接收到消息就记一次心跳超时,超时次数达到设置的上限就认为与服务端的连接断开,从而客户端这边也断开连接
- 3秒没有发送消息就发送一条心跳到服务端(IdleStateHandler自动触发)

在服务端触发器中
- 10秒没有接收到消息就记一次心跳超时,超时次数达到设置的上限就认为与客户端的连接断开,从而服务端这边也断开连接
- 5秒没有发送消息就发送一条心跳到服务端

以此实现客户端/服务端的双向心跳通信,以确保通信通道的可用性,同时为后续的重连机制和监控与报警机制打下基础

---

#### 客户端主动重连机制的设计

大致逻辑为:

当客户端累计若干次(根据使用场景配置)心跳超时,则尝试与服务端重连

重连时,以EventLoop异步调度的形式递归调用n次(根据场景配置)重连函数,每次重连都根据当前的serviceName在已注册的服务当中寻找相应的服务,递归调用n次还未成功则关闭channel

`

    public void reconnect(int attempts) {
        if (attempts > maxRetries) {
            System.err.println("Max retries reached. Giving up.");
            return;
        }

        // 用 EventLoop 异步调度重连
        int delay = retryDelay;
        if (channel != null && channel.eventLoop() != null) {
            channel.eventLoop().schedule(() -> {
                // 在指定延迟之后执行,也就是schedule - 计划
                try {
                    System.out.println("Reconnecting Attempt: " + attempts);
                    connect(serviceName);
                } catch (Exception e) {
                    System.err.println("Reconnect failed: " + e.getMessage());
                    // 递归尝试
                    reconnect(attempts + 1);
                }
            }, delay, TimeUnit.SECONDS);
        } else {
            // fallback:如果没有可用的eventLoop则直接使用线程重试
            new Thread(() -> {
                try {
                    Thread.sleep(delay * 1000L);
                    System.out.println("Reconnecting Attempt: " + attempts);
                    connect(serviceName);
                } catch (Exception e) {
                    System.err.println("Reconnect failed: " + e.getMessage());
                    reconnect(attempts + 1);
                }
            }).start();
        }
    }

`

设计要点

- 如果channel没有问题且eventLoop线程没有问题,那么直接使用现成的eventLoop进行异步操作
- 使用schedule异步,可以不阻塞Netty线程,也不会像while循环一样一直重连
- 成功重连之后像普通的connect一样恢复业务注册,重新发送注册包等

---

### 6.24

#### 阶段性测试与JMeter的初步使用

JMeter目前用于压力测试,模拟高并发场景

对JMeter的大致使用流程总结:

- 由于使用自定义的RcpMessage作为自定义消息类,所以需要使用JMeter的Java请求,于是需要编写Java代码后打成jar包供JMeter使用(此处需要注意,在打包时不能把JMeter相关的依赖包含进去,最好是通过IDEA的Maven打包,否则JMeter无法正常运行)

```
package com.exampler.JMeter;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RpcJavaSampler extends AbstractJavaSamplerClient {

    // 每线程一个Socket
    private static final ThreadLocal<Socket> threadLocalSocket = new ThreadLocal<>();
    private static final ThreadLocal<InputStream> threadLocalIn = new ThreadLocal<>();
    private static final ThreadLocal<OutputStream> threadLocalOut = new ThreadLocal<>();

    private String host;
    private int port;

    @Override
    public Arguments getDefaultParameters() {
        Arguments params = new Arguments();
        params.addArgument("host", "127.0.0.1");
        params.addArgument("port", "8080");
        params.addArgument("json", "{\"type\":\"heartbeat\"}");
        return params;
    }

    @Override
    public void setupTest(JavaSamplerContext context) {
        host = context.getParameter("host");
        port = Integer.parseInt(context.getParameter("port"));
        // 在setupTest不主动连，留给runTest第一次连（每线程独立）
    }

    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();
        String json = context.getParameter("json");
        result.setSamplerData(json);

        try {
            // 拿到线程自己的Socket，如无则新建
            Socket socket = threadLocalSocket.get();
            InputStream in = threadLocalIn.get();
            OutputStream out = threadLocalOut.get();
            if (socket == null || socket.isClosed() || !socket.isConnected()) {
                socket = new Socket(host, port);
                socket.setSoTimeout(5000);
                in = socket.getInputStream();
                out = socket.getOutputStream();
                threadLocalSocket.set(socket);
                threadLocalIn.set(in);
                threadLocalOut.set(out);
            }

            result.sampleStart();

            // 发送请求
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.allocate(4 + body.length);
            buf.putInt(body.length);
            buf.put(body);
            out.write(buf.array());
            out.flush();

            // 读取响应
            byte[] lenBytes = readFully(in, 4);
            if (lenBytes == null) throw new IOException("未读到4字节包头");
            int len = ByteBuffer.wrap(lenBytes).getInt();
            byte[] respBytes = readFully(in, len);
            if (respBytes == null) throw new IOException("未读到完整包体");
            String resp = new String(respBytes, StandardCharsets.UTF_8);

            result.setResponseData(resp, "UTF-8");
            result.setResponseMessage("OK");
            result.setSuccessful(true);

        } catch (Exception e) {
            result.setResponseMessage("Exception: " + e);
            result.setResponseData(getStackTrace(e), "UTF-8");
            result.setSuccessful(false);
            e.printStackTrace();
            // 若异常，主动销毁本线程socket，下次重连
            closeThreadSocket();
        } finally {
            result.sampleEnd();
        }
        return result;
    }

    @Override
    public void teardownTest(JavaSamplerContext context) {
        closeThreadSocket();
    }

    // 关闭本线程Socket及流
    private void closeThreadSocket() {
        try {
            if (threadLocalIn.get() != null) threadLocalIn.get().close();
        } catch (Exception ignore) {}
        try {
            if (threadLocalOut.get() != null) threadLocalOut.get().close();
        } catch (Exception ignore) {}
        try {
            if (threadLocalSocket.get() != null) threadLocalSocket.get().close();
        } catch (Exception ignore) {}
        threadLocalIn.remove();
        threadLocalOut.remove();
        threadLocalSocket.remove();
    }

    // 辅助方法：确保完整读取指定字节数，否则返回null
    private static byte[] readFully(InputStream in, int len) throws IOException {
        byte[] buf = new byte[len];
        int total = 0;
        while (total < len) {
            int n = in.read(buf, total, len - total);
            if (n < 0) return null;
            total += n;
        }
        return buf;
    }

    // 辅助方法：获取异常堆栈
    private static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
```

然后先在测试类中启动服务端,后在JMeter中配置100条线程,每条线程循环100次,以及配置RpcMessage中的Json

最后启动JMeter,查看IDEA中服务端的Log和JMeter的查看结果树/聚合报告

- IDEA中服务端Log正常输出运行时信息
- JMeter中结果树,所有线程运行全正常,所有通信全正常
- JMeter中聚合报告,报告如下

| Label    | #样本 | 平均值 | 中位数 | 90%百分位 | 95%百分位 | 99%百分位 | 最小值 | 最大值 | 异常 % | 吞吐量    | 接收 KB/sec | 发送 KB/sec |
|----------|-------|--------|--------|-----------|-----------|-----------|--------|--------|--------|-----------|-------------|-------------|
| Java请求 | 10000 | 17     | 18     | 26        | 29        | 34        | 0      | 124    | 0.00%  | 4008.0/sec | 481.43      | 0.00        |
| 总体     | 10000 | 17     | 18     | 26        | 29        | 34        | 0      | 124    | 0.00%  | 4008.0/sec | 481.43      | 0.00        |

此次测试总结:

- 程序运行正常,至少目前为止客户端向服务端发送方法调用请求,服务端返回简单的程序运行结果,此过程能够正常运行不丢包
- 在100个线程100次循环的高并发环境下也能够正常运行,吞吐量4008.0/sec,说明开发的Rpc框架有应对真正业务中高并发情况的潜力,同时证明了使用zooKeeper作为服务注册与发现也有其可用性,能够姑且保证后面负载均衡部分开发时的基础牢固
- 说明了我的Rpc框架开发到目前为止有一个较好的基础,后续对于高可用性和应对高并发的开发能够不用担心基础部分的问题

未来开发方向 -> 高可用性(警报系统) 应对高并发(负载均衡等)

---
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


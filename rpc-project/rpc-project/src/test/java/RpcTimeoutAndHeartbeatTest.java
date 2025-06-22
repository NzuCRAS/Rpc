import com.example.rpc.client.RpcClient;
import com.example.rpc.client.RpcClientProxy;
import com.example.rpc.protocol.JsonSerializer;
import com.example.rpc.server.RpcServer;
import com.example.rpc.api.HelloService;

public class RpcTimeoutAndHeartbeatTest {
    public static void main(String[] args) throws Exception {
        String zooKeeperHost = "127.0.0.1:2181";
        // 1. 启动服务端（用线程异步）
        RpcServer rpcServer = new RpcServer(8080, zooKeeperHost);
        new Thread(() -> {
            try {
                rpcServer.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        // 2. 等待服务端注册（关键！建议1秒以上）
        Thread.sleep(1500);

        // 3. 客户端测试超时
        RpcClient rpcClient = new RpcClient(zooKeeperHost, new JsonSerializer());
        RpcClientProxy rpcClientProxy = new RpcClientProxy(rpcClient);
        HelloService helloService = rpcClientProxy.getProxy(HelloService.class);

        // 正常调用
        try {
            String result = helloService.sayHello("world");
            System.out.println("result: " + result);
        } catch (Exception e) {
            System.out.println("catch exception: " + e.getMessage());
        }

        // 4. 只要客户端连着，不发请求，观察心跳机制日志
        System.out.println("观察心跳机制，请手动查看服务端和客户端日志输出");
        Thread.sleep(10000); // 观察10s
        rpcServer.stop();
        rpcClient.close();
    }
}
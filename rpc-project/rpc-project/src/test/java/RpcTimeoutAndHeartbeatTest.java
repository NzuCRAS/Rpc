import com.example.rpc.client.RpcClient;
import com.example.rpc.protocol.JsonSerializer;
import com.example.rpc.protocol.RpcMessage;
import com.example.rpc.server.RpcServer;

public class RpcTimeoutAndHeartbeatTest {
    public static void main(String[] args) throws Exception {
        String zooKeeperHost = "127.0.0.1:2181";
        String serviceName = "HelloService";

        // 1. 启动服务端（用线程异步）
        RpcServer rpcServer = new RpcServer(8080, zooKeeperHost, new JsonSerializer());
        new Thread(() -> {
            try {
                rpcServer.start(serviceName);
                System.out.println("RpcServer started");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        // 2. 等待服务端注册（关键！建议1秒以上）
        Thread.sleep(2000);

        // 3. 客户端测试超时
        RpcClient rpcClient = new RpcClient(zooKeeperHost, new JsonSerializer());
        rpcClient.connect(serviceName);

        // 4. 发送正常请求
        RpcMessage request = new RpcMessage();
        request.setType("request");
        request.setMethodName("sayHello");
        request.setServiceName(serviceName);
        request.setParams(new Object[]{"world"});
        try {
            RpcMessage response = rpcClient.sendRequest(request);
            System.out.println("Response: " + response);
        } catch (Exception e) {
            System.out.println("Error:" + e);
        }

        // 4. 只要客户端连着，不发请求，观察心跳机制日志
        System.out.println("观察心跳机制，请手动查看服务端和客户端日志输出");
        Thread.sleep(10000); // 观察10s
        rpcServer.stop();
        rpcClient.close();
    }
}
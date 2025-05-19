import com.example.rpc.client.RpcClient;
import com.example.rpc.client.RpcClientProxy;
import com.example.rpc.server.RpcServer;
import com.example.rpc.service.HelloService;
import com.example.rpc.service.HelloServiceImpl;

public class RpcClientTest {
    public static void main(String[] args) throws Exception {
        String zooKeeperHost = "127.0.0.1:2181";
        RpcServer rpcServer = new RpcServer(8080, zooKeeperHost);
        rpcServer.start();
        // 连接 ZooKeeper 服务端监听的端口
        RpcClient rpcClient = new RpcClient(zooKeeperHost);
        RpcClientProxy rpcClientProxy = new RpcClientProxy(rpcClient);
        HelloService helloService = rpcClientProxy.getProxy(HelloService.class);
        String result = helloService.sayHello("world");
        System.out.println("result: " + result);
    }
}

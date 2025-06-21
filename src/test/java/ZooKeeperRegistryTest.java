import com.example.rpc.client.RpcClient;
import com.example.rpc.protocol.JsonSerializer;
import com.example.rpc.protocol.Serializer;
import com.example.rpc.server.RpcServer;

public class ZooKeeperRegistryTest {
    public static void main(String[] args) throws Exception {
        String zooKeeperHost = "127.0.0.1:2181";

        // 启动服务端
        RpcServer rpcServer = new RpcServer(8080, zooKeeperHost);
        rpcServer.start();

        // 启动客户端
        Serializer serializer = new JsonSerializer();
        RpcClient rpcClient = new RpcClient(zooKeeperHost, serializer);
        rpcClient.connect("HelloService");

        // 发送请求


        rpcClient.close();
        rpcServer.stop();
    }
}

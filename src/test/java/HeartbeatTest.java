import com.example.rpc.protocol.JsonSerializer;
import com.example.rpc.server.RpcServer;
import org.junit.Test;

public class HeartbeatTest {
    String zooKeeperHost = "127.0.0.1:2181";
    String serviceName = "HelloService";

    @Test
    public void test() throws Exception {
        RpcServer rpcServer = new RpcServer(8080, zooKeeperHost, new JsonSerializer());
        rpcServer.start("HelloService");
    }
}

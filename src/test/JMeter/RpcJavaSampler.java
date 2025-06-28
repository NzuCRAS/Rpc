import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.AbstractJavaSamplerClient;
import org.apache.jmeter.samplers.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

import java.io.OutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RpcJavaSampler extends AbstractJavaSamplerClient {

    @Override
    public Arguments getDefaultParameters() {
        Arguments params = new Arguments();
        params.addArgument("host", "127.0.0.1");
        params.addArgument("port", "8080");
        params.addArgument("json", "{\"type\":\"heartbeat\"}");
        return params;
    }

    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();
        String host = context.getParameter("host");
        int port = Integer.parseInt(context.getParameter("port"));
        String json = context.getParameter("json");
        try (Socket socket = new Socket(host, port)) {
            result.sampleStart();

            // 构造“包头+包体”：4字节长度 + JSON字节
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.allocate(4 + body.length);
            buf.putInt(body.length);
            buf.put(body);
            OutputStream out = socket.getOutputStream();
            out.write(buf.array());
            out.flush();

            // 读取响应（假设响应格式同样是4字节包长+内容）
            InputStream in = socket.getInputStream();
            byte[] lenBytes = in.readNBytes(4);
            int len = ByteBuffer.wrap(lenBytes).getInt();
            byte[] respBytes = in.readNBytes(len);
            String resp = new String(respBytes, StandardCharsets.UTF_8);

            result.setResponseData(resp, "UTF-8");
            result.setResponseMessage("OK");
            result.setSuccessful(true);

        } catch (Exception e) {
            result.setResponseMessage("Exception: " + e);
            result.setSuccessful(false);
        } finally {
            result.sampleEnd();
        }
        return result;
    }
}
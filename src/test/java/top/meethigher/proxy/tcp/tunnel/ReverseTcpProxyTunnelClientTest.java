package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.proto.TunnelMessage;

import java.util.concurrent.locks.LockSupport;

public class ReverseTcpProxyTunnelClientTest {


    private static final Logger log = LoggerFactory.getLogger(ReverseTcpProxyTunnelClientTest.class);

    private static Vertx vertx;

    private static NetClient netClient;


    @Before
    public void setUp() throws Exception {
        vertx = Vertx.vertx();
        netClient = vertx.createNetClient();
    }

    /**
     * 测试错误消息
     */
    @Test
    public void testErrorMsg() {
        netClient.connect(44444, "127.0.0.1")
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        NetSocket socket = ar.result();
                        socket.pause();
                        socket.closeHandler(v -> {
                            log.error("连接被关闭");
                            System.exit(1);
                        });
                        socket.write("hello world");
                        socket.resume();
                    } else {
                        ar.cause().printStackTrace();
                        System.exit(1);
                    }
                });
        LockSupport.park();
    }

    @Test
    public void testNoAuthMsg() {
        netClient.connect(44444, "127.0.0.1")
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        NetSocket socket = ar.result();
                        socket.pause();
                        socket.closeHandler(v -> {
                            log.error("连接被关闭");
                            System.exit(1);
                        });
                        socket.write(TunnelMessageCodec.encode(TunnelMessageType.HEARTBEAT.code(),
                                TunnelMessage.Heartbeat.newBuilder().setTimestamp(System.currentTimeMillis())
                                        .build().toByteArray()));

                        socket.resume();
                    } else {
                        ar.cause().printStackTrace();
                        System.exit(1);
                    }
                });

        LockSupport.park();
    }

    @Test
    public void client() {
        ReverseTcpProxyTunnelClient.create(vertx, netClient).connect("127.0.0.1", 44444);



        LockSupport.park();
    }
}
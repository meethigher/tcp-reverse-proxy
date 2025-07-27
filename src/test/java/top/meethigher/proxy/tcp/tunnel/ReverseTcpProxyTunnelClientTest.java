package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
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
//        netClient.connect(44444, "127.0.0.1")
//                .onComplete(ar -> {
//                    if (ar.succeeded()) {
//                        NetSocket socket = ar.result();
//                        socket.pause();
//                        socket.closeHandler(v -> {
//                            log.error("连接被关闭");
//                            System.exit(1);
//                        });
//                        socket.write("hello world");
//                        socket.resume();
//                    } else {
//                        ar.cause().printStackTrace();
//                        System.exit(1);
//                    }
//                });

        // 模拟故意制造错误的请求头长度。服务端可以配置闲时超时参数解决。注意要大于客户端心跳的频率
        netClient.connect(44444, "127.0.0.1")
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        NetSocket socket = ar.result();
                        socket.pause();
                        socket.closeHandler(v -> {
                            log.error("连接被关闭");
                            System.exit(1);
                        });

                        // 模拟暴力攻击
                        vertx.setPeriodic(500, id -> {
                            byte[] byteArray = TunnelMessage.OpenDataPort.newBuilder()
                                    .setDataProxyName("ssh-proxy")
                                    .setDataProxyPort(2222)
                                    .setSecret("0123456789")
                                    .build().toByteArray();
                            int totalLength = 4 + 2 + byteArray.length;
                            // 模拟恶意制造消息长度
                            totalLength += 100;
                            Buffer buffer = Buffer.buffer();
                            buffer.appendInt(totalLength);
                            buffer.appendShort(TunnelMessageType.OPEN_DATA_PORT.code());
                            buffer.appendBytes(byteArray);
                            socket.write(buffer);
                        });

//                        socket.write(buffer).onComplete(ar1 -> {
//                            // 发送一条正常消息
//                            socket.write(new TunnelMessageCodec().encode(TunnelMessageType.OPEN_DATA_PORT.code(),
//                                    TunnelMessage.OpenDataPort.newBuilder()
//                                            .setSecret("0123456789")
//                                            .setDataProxyName("ssh-proxy")
//                                            .setDataProxyPort(2222)
//                                            .build().toByteArray()
//                            ));
//                        });
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
        Vertx vertx = Vertx.vertx(new VertxOptions().setAddressResolverOptions(
                new AddressResolverOptions().setQueryTimeout(2000)
        ));
        // ssh内网穿透
        ReverseTcpProxyTunnelClient.create(ReverseTcpProxyTunnelClientTest.vertx, vertx.createNetClient(), Tunnel.SECRET_DEFAULT)
                .backendHost("10.0.0.30")
                .backendPort(22)
                .dataProxyName("ssh-proxy")
                .dataProxyHost("10.0.0.1")
                .dataProxyPort(2222)
                .connect("10.0.0.1", 44444);
        LockSupport.park();
    }
}
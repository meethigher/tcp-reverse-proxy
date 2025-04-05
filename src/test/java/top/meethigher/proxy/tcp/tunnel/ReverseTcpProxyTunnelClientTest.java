package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.handler.TunnelHandler;

import java.util.concurrent.locks.LockSupport;

public class ReverseTcpProxyTunnelClientTest {


    private static final Logger log = LoggerFactory.getLogger(ReverseTcpProxyTunnelClientTest.class);

    @Test
    public void name() {
        Vertx vertx = Vertx.vertx();
        NetClient netClient = vertx.createNetClient();
        ReverseTcpProxyTunnelClient client = ReverseTcpProxyTunnelClient.create(vertx, netClient);
        client.connect("10.0.0.1", 44444);
        Buffer buffer = TunnelMessageCodec.encode(TunnelMessageType.AUTH.code(), Buffer.buffer("hello world").getBytes());
        client.onConnected(new TunnelHandler() {
            @Override
            public void handle(Vertx vertx, NetSocket netSocket, Buffer buffer) {
                vertx.setPeriodic(3000, id -> {
                    log.info("发送心跳");
                    netSocket.write(client.encode(TunnelMessageType.HEARTBEAT, new byte[0]));
                });
            }
        });
        client.on(TunnelMessageType.HEARTBEAT_ACK, new TunnelHandler() {
            @Override
            public void handle(Vertx vertx, NetSocket netSocket, Buffer buffer) {
                log.info("收到心跳响应");
            }
        });


        LockSupport.park();
    }
}
package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetServerOptions;
import org.junit.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

public class ReverseTcpProxyTunnelServerTest {


    @Test
    public void server() {
        Vertx vertx = Vertx.vertx();
        ReverseTcpProxyTunnelServer server = ReverseTcpProxyTunnelServer.create(vertx, vertx.createNetServer(
                        new NetServerOptions().setTcpNoDelay(true)
                ), Tunnel.SECRET_DEFAULT, new ConcurrentHashMap<>())
                .port(44444)
                .judgeDelay(300);
        server.start();
        LockSupport.park();
    }
}
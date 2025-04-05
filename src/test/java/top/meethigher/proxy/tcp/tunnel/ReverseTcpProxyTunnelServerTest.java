package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import org.junit.Test;

import java.util.concurrent.locks.LockSupport;

public class ReverseTcpProxyTunnelServerTest {


    @Test
    public void name() {
        Vertx vertx = Vertx.vertx();
        NetServer netServer = vertx.createNetServer();
        ReverseTcpProxyTunnelServer.create(vertx, netServer)
                .start();
        LockSupport.park();
    }
}
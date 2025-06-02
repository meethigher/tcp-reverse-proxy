package top.meethigher.proxy.tcp.tunnel.issue9;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.ReverseTcpProxyTunnelServer;

import java.util.concurrent.TimeUnit;

public class Issue9TunnelServer {

    private static final Vertx vertx = Vertx.vertx();
    private static final Logger log = LoggerFactory.getLogger(Issue9TunnelServer.class);

    public static void main(String[] args) {
        NetServer netServer = vertx.createNetServer(
                new NetServerOptions()
                        .setIdleTimeout(999999999)
                        .setIdleTimeoutUnit(TimeUnit.MILLISECONDS));
        ReverseTcpProxyTunnelServer.create(vertx, netServer)
                .heartbeatDelay(888888888)
                .judgeDelay(50)
                .port(44444)
                .start();
    }
}

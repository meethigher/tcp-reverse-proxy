package top.meethigher.proxy.tcp.tunnel.issue9;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.ReverseTcpProxyTunnelClient;

import java.util.concurrent.TimeUnit;

public class Issue9TunnelClient {

    private static final Vertx vertx = Vertx.vertx();
    private static final Logger log = LoggerFactory.getLogger(Issue9TunnelClient.class);

    public static void main(String[] args) {
        NetClient netClient = vertx.createNetClient(new NetClientOptions()
                .setIdleTimeout(999999999)
                .setIdleTimeoutUnit(TimeUnit.MILLISECONDS));
        ReverseTcpProxyTunnelClient.create(vertx, netClient)
                .dataProxyPort(808)
                .dataProxyHost("127.0.0.1")
                .dataProxyName("http")
                .backendHost("127.0.0.1")
                .backendPort(80)
                .connect("127.0.0.1", 44444);
    }
}

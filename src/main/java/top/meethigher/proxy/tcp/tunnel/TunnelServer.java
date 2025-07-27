package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 适用于 Tunnel 编解码规范的通用 Server
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/04/05 10:28
 */
public abstract class TunnelServer extends Tunnel {
    private static final Logger log = LoggerFactory.getLogger(TunnelServer.class);

    protected final NetServer netServer;

    protected TunnelServer(Vertx vertx, NetServer netServer, String secret) {
        super(vertx, secret);
        this.netServer = netServer;
    }
}

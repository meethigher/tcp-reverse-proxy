package top.meethigher.proxy.tcp.tunnel.handler;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;


/**
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/04/05 01:42
 */
public interface TunnelHandler {

    void handle(Vertx vertx, NetSocket netSocket, Buffer buffer);
}

package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageParser;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.handler.TunnelHandler;

/**
 * 适用于 Tunnel 编解码规范的通用 Server
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/04/05 10:28
 */
public abstract class TunnelServer extends Tunnel {
    private static final Logger log = LoggerFactory.getLogger(TunnelServer.class);

    protected final Vertx vertx;
    protected final NetServer netServer;

    protected TunnelServer(Vertx vertx, NetServer netServer) {
        this.vertx = vertx;
        this.netServer = netServer;
    }

    @Override
    public void onConnected(TunnelHandler tunnelHandler) {
        tunnelHandlers.put(null, tunnelHandler);
    }

    @Override
    public void on(TunnelMessageType type, TunnelHandler tunnelHandler) {
        tunnelHandlers.put(type, tunnelHandler);
    }

    @Override
    public TunnelMessageParser decode(NetSocket socket) {
        return new TunnelMessageParser(buffer -> {
            TunnelMessageCodec.DecodedMessage decodedMessage = TunnelMessageCodec.decode(buffer);
            TunnelMessageType type = TunnelMessageType.fromCode(decodedMessage.type);
            for (TunnelMessageType tunnelMessageType : tunnelHandlers.keySet()) {
                if (type == tunnelMessageType) {
                    TunnelHandler tunnelHandler = tunnelHandlers.get(tunnelMessageType);
                    if (tunnelHandler != null) {
                        tunnelHandler.handle(vertx, socket, buffer);
                    }
                }
            }
        }, socket);
    }
}

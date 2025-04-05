package top.meethigher.proxy.tcp.tunnel.handler;

import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;

public abstract class TunnelHandler {

    private static final Logger log = LoggerFactory.getLogger(TunnelHandler.class);
    protected final NetSocket netSocket;

    protected final TunnelMessageType type;

    protected final TunnelMessageType respType;

    protected final byte[] bodyBytes;

    public TunnelHandler(NetSocket netSocket, TunnelMessageType type, TunnelMessageType respType, byte[] bodyBytes) {
        this.netSocket = netSocket;
        this.type = type;
        this.respType = respType;
        this.bodyBytes = bodyBytes;
    }

    public void handler() {
        boolean result = doHandler();
        log.debug("received message type = {}, handle result = {}", type, result);
    }


    protected abstract boolean doHandler();


}

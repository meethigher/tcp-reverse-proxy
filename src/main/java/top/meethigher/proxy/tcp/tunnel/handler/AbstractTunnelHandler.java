package top.meethigher.proxy.tcp.tunnel.handler;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;


/**
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/04/05 01:47
 */
public abstract class AbstractTunnelHandler implements TunnelHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractTunnelHandler.class);

    @Override
    public void handle(Vertx vertx, NetSocket netSocket, Buffer buffer) {
        TunnelMessageCodec.DecodedMessage decodedMessage = TunnelMessageCodec.decode(buffer);
        TunnelMessageType type = TunnelMessageType.fromCode(decodedMessage.type);
        boolean result = doHandle(vertx, netSocket, type, decodedMessage.body);
        log.debug("received message type = {}, handle result = {}", type, result);
    }

    /**
     * 执行逻辑，并返回执行结果。true表示成功
     */
    protected abstract boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes);
}

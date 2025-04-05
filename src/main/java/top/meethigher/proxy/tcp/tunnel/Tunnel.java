package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageParser;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.handler.TunnelHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 封装通用的 TCP 交互API
 * 参考<a href="https://github.com/socketio/socket.io-client-java/blob/socket.io-client-2.1.0/src/main/java/io/socket/client/Socket.java">socket.io-client-java</a>
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/04/05 02:10
 */
public abstract class Tunnel {

    /**
     * 监听消息类型 {@code TunnelMessageType} 以及触发的动作 {@code TunnelHandler}
     */
    protected Map<TunnelMessageType, TunnelHandler> tunnelHandlers = new LinkedHashMap<>();

    /**
     * 监听socket连接成功触发的动作
     */
    public abstract void onConnected(TunnelHandler tunnelHandler);

    /**
     * 监听 {@code TunnelMessageType} 事件
     */
    public abstract void on(TunnelMessageType type, TunnelHandler tunnelHandler);

    public Buffer encode(TunnelMessageType type, byte[] bodyBytes) {
        return TunnelMessageCodec.encode(type.code(), bodyBytes);
    }

    public abstract TunnelMessageParser decode(NetSocket socket);
}

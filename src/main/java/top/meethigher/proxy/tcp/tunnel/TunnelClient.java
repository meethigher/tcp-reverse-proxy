package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageParser;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.handler.TunnelHandler;

/**
 * 适用于 Tunnel 编解码规范的通用 Client
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/04/05 10:28
 */
public abstract class TunnelClient extends Tunnel {

    private static final Logger log = LoggerFactory.getLogger(TunnelClient.class);

    /**
     * 失败重连的时间间隔，单位毫秒
     */
    protected long reconnectDelay;


    protected final NetClient netClient;

    /**
     * Client进行失败重连时的最短间隔时间，单位毫秒
     */
    protected final long minDelay;

    /**
     * Client进行失败重连时的最大间隔时间，单位毫秒
     */
    protected final long maxDelay;

    protected String name;

    /**
     * 内部维护一个长连接 {@code NetSocket}
     * <p>
     * 一个 {@code TunnelClient} 对应一个长连接 {@code NetSocket}
     */
    protected NetSocket netSocket;


    protected TunnelClient(Vertx vertx, NetClient netClient, long minDelay, long maxDelay, String secret) {
        super(vertx, secret);
        this.netClient = netClient;
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
        setReconnectDelay(this.minDelay);
    }

    @Override
    public TunnelMessageParser decode(final NetSocket socket) {
        return new TunnelMessageParser(buffer -> {
            TunnelMessageCodec.DecodedMessage decodedMessage = TunnelMessageCodec.decode(buffer);
            TunnelMessageType type = TunnelMessageType.fromCode(decodedMessage.type);
            for (TunnelMessageType tunnelMessageType : tunnelHandlers.keySet()) {
                if (type == tunnelMessageType) {
                    TunnelHandler tunnelHandler = tunnelHandlers.get(tunnelMessageType);
                    if (tunnelHandler != null) {
                        tunnelHandler.handle(vertx, socket, buffer);
                    }
                    return;
                }
            }
        }, socket);
    }

    /**
     * {@code TunnelClient} 与 {@code TunnelServer} 建立连接，支持断连重试
     *
     * @param host ip或域名
     * @param port 端口
     */
    public void connect(final String host, final int port) {
        log.debug("{}: client connect {}:{} ...", name, host, port);
        netClient.connect(port, host).onComplete(ar -> handleConnectCompleteAsyncResult(ar, host, port));
    }

    /**
     * {@code TunnelClient} 向 {@code TunnelServer} 发送数据
     *
     * @param type      消息类型
     * @param bodyBytes 消息体，使用 protobuf {@code TunnelMessage} 进行编解码
     */
    public void emit(final TunnelMessageType type, final byte[] bodyBytes) {
        if (netSocket == null) {
            log.warn("{}: socket is closed", name);
        } else {
            netSocket.write(encode(type, bodyBytes));
        }
    }

    /**
     * client完成连接后的业务逻辑
     *
     * @param ar   完成结果
     * @param host 连接主机地址
     * @param port 连接端口
     */
    protected void handleConnectCompleteAsyncResult(final AsyncResult<NetSocket> ar,
                                                    final String host, final int port) {
        if (ar.succeeded()) {
            setReconnectDelay(this.minDelay);

            NetSocket socket = ar.result();
            this.netSocket = socket;
            socket.pause();
            socket.closeHandler(v -> {
                log.warn("{}: closed {} -- {}, after {} ms will reconnect",
                        name,
                        socket.localAddress(),
                        socket.remoteAddress(),
                        reconnectDelay);
                this.netSocket = null;
                reconnect(host, port);
            });
            socket.handler(decode(socket));
            log.info("{}: client connected {}:{}", name, host, port);

            // 执行连接成功的Handler
            TunnelHandler tunnelHandler = tunnelHandlers.get(null);
            if (tunnelHandler != null) {
                tunnelHandler.handle(vertx, socket, null);
            }
            socket.resume();
        } else {
            Throwable e = ar.cause();
            log.error("{}: client connect {}:{} error, after {} ms will reconnect",
                    name,
                    host,
                    port,
                    reconnectDelay,
                    e);
            this.netSocket = null;
            reconnect(host, port);
        }
    }

    protected void setReconnectDelay(final long delay) {
        this.reconnectDelay = delay;
    }

    /**
     * 采用指数退避策略进行失败重连
     *
     * @param host ip或域名
     * @param port 端口
     */
    protected void reconnect(final String host, final int port) {
        vertx.setTimer(reconnectDelay, id -> {
            log.debug("{}: client reconnect {}:{} ...", name, host, port);
            connect(host, port);
            setReconnectDelay(Math.min(reconnectDelay * 2, this.maxDelay));
        });
    }

}

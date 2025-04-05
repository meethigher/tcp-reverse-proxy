package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageParser;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.handler.TunnelHandler;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 一个{@code ReverseTcpProxyTunnelClient} 对应一个失败重连的 TCP 连接。如果需要多个 TCP 连接，那么就需要创建多个 {@code ReverseTcpProxyTunnelClient} 实例
 *
 * <p>背景：</p><p>我近期买了个树莓派，但是又不想随身带着树莓派，因此希望可以公网访问。</p>
 * <p>
 * 但是使用<a href="https://github.com/fatedier/frp">fatedier/frp</a>的过程中，不管在Windows还是Linux，都被扫出病毒了。
 * 而且这还是Golang自身的问题，参考<a href="https://go.dev/doc/faq#virus">Why does my virus-scanning software think my Go distribution or compiled binary is infected?</a>
 * 因此自己使用Java实现一套类似frp的工具，还是很有必要的。
 * </p>
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/04/01 23:25
 */
public class ReverseTcpProxyTunnelClient extends Tunnel {
    private static final Logger log = LoggerFactory.getLogger(ReverseTcpProxyTunnelClient.class);


    protected static final long MIN_DELAY_DEFAULT = 1000;
    protected static final long MAX_DELAY_DEFAULT = 64000;
    protected static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();


    /**
     * 连接的目标控制主机
     */
    protected String controlHost = "127.0.0.1";

    /**
     * 连接的目标控制端口
     */
    protected int controlPort = 44444;

    /**
     * 失败重连的时间间隔，单位毫秒
     */
    protected long reconnectDelay;

    /**
     * 内部维护一个长连接socket
     */
    protected NetSocket netSocket;

    protected final Vertx vertx;
    protected final NetClient netClient;
    protected final String name;
    /**
     * Client进行失败重连时的最短间隔时间，单位毫秒
     */
    protected final long minDelay;

    /**
     * Client进行失败重连时的最大间隔时间，单位毫秒
     */
    protected final long maxDelay;


    protected static String generateName() {
        final String prefix = "ReverseTcpProxyTunnelClient-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger("top.meethigher.proxy.tcp.tunnel.ReverseTcpProxyTunnelClient.name", 0) + 1);
                System.setProperty("top.meethigher.proxy.tcp.tunnel.ReverseTcpProxyTunnelClient.name", next);
                return prefix + next;
            }
        } catch (Exception e) {
            final ThreadLocalRandom random = ThreadLocalRandom.current();
            final StringBuilder sb = new StringBuilder(prefix);
            for (int i = 0; i < 4; i++) {
                sb.append(ID_CHARACTERS[random.nextInt(62)]);
            }
            return sb.toString();
        }
    }

    protected ReverseTcpProxyTunnelClient(Vertx vertx, NetClient netClient, String name, long minDelay, long maxDelay) {
        this.vertx = vertx;
        this.netClient = netClient;
        this.name = name;
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
        this.reconnectDelay = this.minDelay;
    }

    public static ReverseTcpProxyTunnelClient create(Vertx vertx, NetClient netClient, long minDelay, long maxDelay, String name) {
        return new ReverseTcpProxyTunnelClient(vertx, netClient, name, minDelay, maxDelay);
    }

    public static ReverseTcpProxyTunnelClient create(Vertx vertx, NetClient netClient, String name) {
        return new ReverseTcpProxyTunnelClient(vertx, netClient, name, MIN_DELAY_DEFAULT, MAX_DELAY_DEFAULT);
    }

    public static ReverseTcpProxyTunnelClient create(Vertx vertx, NetClient netClient) {
        return new ReverseTcpProxyTunnelClient(vertx, netClient, generateName(), MIN_DELAY_DEFAULT, MAX_DELAY_DEFAULT);
    }

    public static ReverseTcpProxyTunnelClient create(Vertx vertx) {
        return new ReverseTcpProxyTunnelClient(vertx, vertx.createNetClient(), generateName(), MIN_DELAY_DEFAULT, MAX_DELAY_DEFAULT);
    }


    public void connect(String host, int port) {
        this.controlHost = host;
        this.controlPort = port;
        log.debug("client connect {}:{} ...", this.controlHost, this.controlPort);

        Handler<AsyncResult<NetSocket>> asyncResultHandler = ar -> {
            if (ar.succeeded()) {
                setReconnectDelay(this.minDelay);
                NetSocket socket = ar.result();
                this.netSocket = socket;
                socket.pause();
                socket.closeHandler(v -> {
                    log.debug("closed {} -- {}, after {} ms will reconnect",
                            socket.localAddress(),
                            socket.remoteAddress(),
                            reconnectDelay);
                    reconnect();
                });
                socket.handler(decode(socket));
                log.info("client connected {}:{}", controlHost, controlPort);
                TunnelHandler tunnelHandler = tunnelHandlers.get(null);
                if (tunnelHandler != null) {
                    tunnelHandler.handle(vertx, socket, null);
                }
                socket.resume();
            } else {
                Throwable e = ar.cause();
                log.error("client connect {}:{} error, after {} ms will reconnect",
                        host,
                        port,
                        reconnectDelay,
                        e);
                reconnect();
            }
        };
        netClient.connect(this.controlPort, this.controlHost).onComplete(asyncResultHandler);
    }

    public void emit(Buffer buffer) {
        if (netSocket == null) {
            log.warn("socket is closed");
        } else {
            netSocket.write(buffer);
        }
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

    protected void setReconnectDelay(long delay) {
        this.reconnectDelay = delay;
    }

    /**
     * 采用指数退避策略进行失败重连
     */
    protected void reconnect() {
        netSocket = null;
        vertx.setTimer(reconnectDelay, id -> {
            log.info("client reconnect {}:{} ...", this.controlHost, this.controlPort);
            connect(this.controlHost, this.controlPort);
            setReconnectDelay(Math.min(reconnectDelay * 2, this.maxDelay));
        });
    }
}

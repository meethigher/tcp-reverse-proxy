package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.handler.AbstractTunnelHandler;
import top.meethigher.proxy.tcp.tunnel.proto.TunnelMessage;

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
public class ReverseTcpProxyTunnelClient extends TunnelClient {
    private static final Logger log = LoggerFactory.getLogger(ReverseTcpProxyTunnelClient.class);


    protected static final long HEARTBEAT_DELAY_DEFAULT = 5000;// 毫秒
    protected static final long MIN_DELAY_DEFAULT = 1000;// 毫秒
    protected static final long MAX_DELAY_DEFAULT = 64000;// 毫秒
    protected static final String TOKEN_DEFAULT = "123456789";
    protected static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();


    protected final long heartbeatDelay;
    protected final String token;
    protected final String name;


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

    protected ReverseTcpProxyTunnelClient(Vertx vertx, NetClient netClient,
                                          long minDelay, long maxDelay, long heartbeatDelay,
                                          String token, String name) {
        super(vertx, netClient, minDelay, maxDelay);
        this.heartbeatDelay = heartbeatDelay;
        this.token = token;
        this.name = name;
        addMessageHandler();
    }

    /**
     * 注册内网穿透的监听逻辑
     */
    protected void addMessageHandler() {
        // 监听连接成功事件
        this.onConnected((vertx, netSocket, buffer) -> emit(TunnelMessageType.AUTH, TunnelMessage.Auth
                .newBuilder()
                .setToken(token)
                .build()
                .toByteArray()));
        // 监听授权响应事件
        this.on(TunnelMessageType.AUTH_ACK, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                boolean result = false;
                try {
                    TunnelMessage.AuthAck ack = TunnelMessage.AuthAck.parseFrom(bodyBytes);
                    result = ack.getSuccess();
                    if (result) {
                        vertx.setTimer(heartbeatDelay, id -> emit(TunnelMessageType.HEARTBEAT,
                                TunnelMessage.Heartbeat.newBuilder()
                                        .setTimestamp(System.currentTimeMillis())
                                        .build()
                                        .toByteArray()));
                    }
                } catch (Exception e) {
                }
                return result;
            }
        });
        // 监听心跳响应事件
        this.on(TunnelMessageType.HEARTBEAT_ACK, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                try {
                    vertx.setTimer(heartbeatDelay, id -> emit(TunnelMessageType.HEARTBEAT,
                            TunnelMessage.Heartbeat.newBuilder()
                                    .setTimestamp(System.currentTimeMillis())
                                    .build()
                                    .toByteArray()));
                } catch (Exception e) {
                }
                return true;
            }
        });
    }

    public static ReverseTcpProxyTunnelClient create(Vertx vertx, NetClient netClient, long minDelay, long maxDelay, long heartbeatDelay, String token, String name) {
        return new ReverseTcpProxyTunnelClient(vertx, netClient, minDelay, maxDelay, heartbeatDelay, token, name);
    }

    public static ReverseTcpProxyTunnelClient create(Vertx vertx, NetClient netClient, String token) {
        return new ReverseTcpProxyTunnelClient(vertx, netClient, MIN_DELAY_DEFAULT, MAX_DELAY_DEFAULT, HEARTBEAT_DELAY_DEFAULT, token, generateName());
    }


    public static ReverseTcpProxyTunnelClient create(Vertx vertx, NetClient netClient) {
        return new ReverseTcpProxyTunnelClient(vertx, netClient, MIN_DELAY_DEFAULT, MAX_DELAY_DEFAULT, HEARTBEAT_DELAY_DEFAULT, TOKEN_DEFAULT, generateName());
    }

    public static ReverseTcpProxyTunnelClient create(Vertx vertx) {
        return new ReverseTcpProxyTunnelClient(vertx, vertx.createNetClient(), MIN_DELAY_DEFAULT, MAX_DELAY_DEFAULT, HEARTBEAT_DELAY_DEFAULT, TOKEN_DEFAULT, generateName());
    }


}

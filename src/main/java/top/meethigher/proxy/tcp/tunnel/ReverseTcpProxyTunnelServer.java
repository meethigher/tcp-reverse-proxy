package top.meethigher.proxy.tcp.tunnel;


import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.handler.AbstractTunnelHandler;
import top.meethigher.proxy.tcp.tunnel.handler.TunnelHandler;
import top.meethigher.proxy.tcp.tunnel.proto.TunnelMessage;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
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
public class ReverseTcpProxyTunnelServer extends TunnelServer {

    private static final Logger log = LoggerFactory.getLogger(ReverseTcpProxyTunnelServer.class);
    protected static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    protected static final String TOKEN_DEFAULT = "123456789";


    protected String host = "0.0.0.0";
    protected int port = 44444;
    protected Set<NetSocket> authedSockets = new HashSet<>(); // 授权成功的socket列表

    protected final String token;
    protected final String name;
    protected final Handler<NetSocket> connectHandler;

    public ReverseTcpProxyTunnelServer(Vertx vertx, NetServer netServer, String token, String name) {
        super(vertx, netServer);
        this.token = token;
        this.name = name;
        this.connectHandler = socket -> {
            socket.pause();
            socket.handler(decode(socket));
            socket.closeHandler(v -> {
                log.debug("closed {} -- {}", socket.remoteAddress(), socket.localAddress());
                authedSockets.remove(socket);
            });
            TunnelHandler connectedHandler = tunnelHandlers.get(null);
            if (connectedHandler != null) {
                connectedHandler.handle(vertx, socket, Buffer.buffer());
            }
            socket.resume();
        };
        addMessageHandler();
    }

    /**
     * 注册内网穿透的监听逻辑
     */
    protected void addMessageHandler() {
        // 监听连接成功事件
        this.onConnected((vertx1, netSocket, buffer) -> log.debug("{} connected", netSocket.remoteAddress()));

        // 监听授权消息
        this.on(TunnelMessageType.AUTH, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                boolean result = false;
                try {
                    TunnelMessage.Auth parsed = TunnelMessage.Auth.parseFrom(bodyBytes);
                    if (token.equals(parsed.getToken())) {
                        result = true;
                        netSocket.write(encode(TunnelMessageType.AUTH_ACK,
                                        TunnelMessage.AuthAck.newBuilder()
                                                .setSuccess(result)
                                                .setMessage("success")
                                                .build().toByteArray()))
                                .onComplete(ar -> {
                                    authedSockets.add(netSocket);
                                });
                    } else {
                        netSocket.write(encode(TunnelMessageType.AUTH_ACK,
                                        TunnelMessage.AuthAck.newBuilder()
                                                .setSuccess(result)
                                                .setMessage("failure")
                                                .build().toByteArray()))
                                .onComplete(ar -> {
                                    // 鉴权失败，服务端主动关闭连接
                                    netSocket.close();
                                });
                    }
                } catch (Exception e) {
                }
                return result;
            }
        });

        // 监听心跳
        this.on(TunnelMessageType.HEARTBEAT, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                if (authedSockets.contains(netSocket)) {
                    netSocket.write(encode(TunnelMessageType.HEARTBEAT_ACK,
                            TunnelMessage.HeartbeatAck.newBuilder().setTimestamp(System.currentTimeMillis()).build().toByteArray()));
                    return true;
                } else {
                    // 未经授权的连接，直接关闭
                    netSocket.close();
                    return false;
                }
            }
        });

        // 监听开通端口请求
        this.on(TunnelMessageType.OPEN_PORT, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                return false;
            }
        });
    }

    public static ReverseTcpProxyTunnelServer create(Vertx vertx, NetServer netServer, String token, String name) {
        return new ReverseTcpProxyTunnelServer(vertx, netServer, token, name);
    }

    public static ReverseTcpProxyTunnelServer create(Vertx vertx, NetServer netServer, String token) {
        return new ReverseTcpProxyTunnelServer(vertx, netServer, token, generateName());
    }

    public static ReverseTcpProxyTunnelServer create(Vertx vertx, NetServer netServer) {
        return new ReverseTcpProxyTunnelServer(vertx, netServer, TOKEN_DEFAULT, generateName());
    }

    public static ReverseTcpProxyTunnelServer create(Vertx vertx) {
        return new ReverseTcpProxyTunnelServer(vertx, vertx.createNetServer(), TOKEN_DEFAULT, generateName());
    }


    protected static String generateName() {
        final String prefix = "ReverseTcpProxyTunnelServer-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger("top.meethigher.proxy.tcp.tunnel.ReverseTcpProxyTunnelServer.name", 0) + 1);
                System.setProperty("top.meethigher.proxy.tcp.tunnel.ReverseTcpProxyTunnelServer.name", next);
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

    public void start() {
        Handler<AsyncResult<NetServer>> asyncResultHandler = ar -> {
            if (ar.succeeded()) {
                log.info("{} started on {}:{}", name, host, port);
            } else {
                Throwable e = ar.cause();
                log.error("{} start failed", name, e);
            }
        };
        netServer.connectHandler(connectHandler).exceptionHandler(e -> log.error("connect failed", e));
        netServer.listen(port, host).onComplete(asyncResultHandler);
    }

    public void stop() {
        netServer.close()
                .onSuccess(v -> log.info("{} closed", name))
                .onFailure(e -> log.error("{} close failed", name, e));
    }

}

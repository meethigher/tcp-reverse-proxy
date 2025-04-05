package top.meethigher.proxy.tcp.tunnel;


import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageParser;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.handler.AbstractTunnelHandler;
import top.meethigher.proxy.tcp.tunnel.handler.TunnelHandler;
import top.meethigher.proxy.tcp.tunnel.proto.TunnelMessage;

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
public class ReverseTcpProxyTunnelServer extends Tunnel {

    private static final Logger log = LoggerFactory.getLogger(ReverseTcpProxyTunnelServer.class);
    protected static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    protected static final String TOKEN_DEFAULT = "meethigher";


    protected String host = "0.0.0.0";
    protected int port = 44444;

    protected final Vertx vertx;
    protected final NetServer netServer;
    protected final String token;
    protected final String name;
    protected final Handler<NetSocket> connectHandler;


    private ReverseTcpProxyTunnelServer(Vertx vertx, NetServer netServer, String token, String name) {
        this.vertx = vertx;
        this.netServer = netServer;
        this.token = token;
        this.name = name;
        this.connectHandler = socket -> {
            socket.pause();
            socket.handler(decode(socket));
            socket.closeHandler(v -> log.debug("closed {} -- {}", socket.remoteAddress(), socket.localAddress()));
            TunnelHandler connectedHandler = tunnelHandlers.get(null);
            if (connectedHandler != null) {
                connectedHandler.handle(vertx, socket, Buffer.buffer());
            }
            socket.resume();
        };
        // 注册 Server 端监听事件
        this.onConnected((vertx1, netSocket, buffer) -> log.debug("{} connected", netSocket.remoteAddress()));
        this.on(TunnelMessageType.AUTH, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                return false;
            }
        });
        this.on(TunnelMessageType.HEARTBEAT, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                netSocket.write(encode(TunnelMessageType.HEARTBEAT_ACK,
                        TunnelMessage.HeartbeatAck.newBuilder().setTimestamp(System.currentTimeMillis()).build().toByteArray()));
                return true;
            }
        });
        this.on(TunnelMessageType.OPEN_PORT, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                return false;
            }
        });
        this.on(TunnelMessageType.CONNECT_PORT_ACK, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                return false;
            }
        });
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


    protected void receivedMessageHandler(NetSocket socket, Buffer buffer) {

    }


    public static ReverseTcpProxyTunnelServer create(Vertx vertx, NetServer netServer, String token, String name) {
        return new ReverseTcpProxyTunnelServer(vertx, netServer, token, name);
    }

    public static ReverseTcpProxyTunnelServer create(Vertx vertx, NetServer netServer, String name) {
        return new ReverseTcpProxyTunnelServer(vertx, netServer, TOKEN_DEFAULT, name);
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

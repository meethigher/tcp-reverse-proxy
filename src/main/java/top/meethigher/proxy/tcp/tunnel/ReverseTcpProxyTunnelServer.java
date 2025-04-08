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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
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
public class ReverseTcpProxyTunnelServer extends TunnelServer {

    private static final Logger log = LoggerFactory.getLogger(ReverseTcpProxyTunnelServer.class);
    protected static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    protected static final String SECRET_DEFAULT = "0123456789";


    protected String host = "0.0.0.0";
    protected int port = 44444;
    protected Map<NetSocket, DataProxyServer> authedSockets = new LinkedHashMap<>();// 授权成功的socket列表

    protected final String secret;
    protected final String name;
    protected final Handler<NetSocket> connectHandler;

    public ReverseTcpProxyTunnelServer(Vertx vertx, NetServer netServer, String secret, String name) {
        super(vertx, netServer);
        this.secret = secret;
        this.name = name;
        this.connectHandler = socket -> {
            socket.pause();
            socket.handler(decode(socket));
            socket.closeHandler(v -> {
                log.debug("closed {} -- {}", socket.remoteAddress(), socket.localAddress());
                DataProxyServer removed = authedSockets.remove(socket);
                if (removed != null) {
                    removed.stop();
                }
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

        // 监听心跳事件
        this.on(TunnelMessageType.HEARTBEAT, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                if (authedSockets.containsKey(netSocket)) {
                    netSocket.write(encode(TunnelMessageType.HEARTBEAT_ACK,
                            TunnelMessage.HeartbeatAck.newBuilder().setTimestamp(System.currentTimeMillis())
                                    .buildPartial().toByteArray()));
                    return true;
                } else {
                    // 未经过授权的心跳，直接拒绝
                    netSocket.close();
                    return false;
                }
            }
        });

        // 监听授权与开通端口事件
        this.on(TunnelMessageType.OPEN_DATA_PORT, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                // 如果授权通过，并且成功开通端口。则返回成功；否则则返回失败，并关闭连接
                boolean result = false;
                try {
                    TunnelMessage.OpenDataPort parsed = TunnelMessage.OpenDataPort.parseFrom(bodyBytes);
                    TunnelMessage.OpenDataPortAck.Builder builder = TunnelMessage.OpenDataPortAck
                            .newBuilder();
                    if (secret.equals(parsed.getSecret())) {
                        final DataProxyServer dataProxyServer = new DataProxyServer(vertx, parsed.getDataProxyName(), parsed.getDataProxyPort());
                        if (dataProxyServer.startSync()) {
                            result = true;
                            builder.setSuccess(result).setMessage("success");
                            netSocket.write(encode(TunnelMessageType.OPEN_DATA_PORT_ACK,
                                    builder.build().toByteArray()));
                            authedSockets.put(netSocket, dataProxyServer);
                        } else {
                            builder.setSuccess(result).setMessage("fail to open data port " + parsed.getDataProxyPort());
                            netSocket.write(encode(TunnelMessageType.OPEN_DATA_PORT_ACK,
                                    builder.build().toByteArray())).onComplete(ar -> netSocket.close());
                        }

                    } else {
                        TunnelMessage.OpenDataPortAck ack = TunnelMessage.OpenDataPortAck
                                .newBuilder()
                                .setSuccess(result)
                                .setMessage("your secret is incorrect!")
                                .build();
                        netSocket.write(encode(TunnelMessageType.OPEN_DATA_PORT_ACK,
                                ack.toByteArray())).onComplete(ar -> netSocket.close());
                    }
                } catch (Exception e) {
                }
                return result;
            }
        });
    }

    public static ReverseTcpProxyTunnelServer create(Vertx vertx, NetServer netServer, String secret, String name) {
        return new ReverseTcpProxyTunnelServer(vertx, netServer, secret, name);
    }

    public static ReverseTcpProxyTunnelServer create(Vertx vertx, NetServer netServer, String secret) {
        return new ReverseTcpProxyTunnelServer(vertx, netServer, secret, generateName());
    }

    public static ReverseTcpProxyTunnelServer create(Vertx vertx, NetServer netServer) {
        return new ReverseTcpProxyTunnelServer(vertx, netServer, SECRET_DEFAULT, generateName());
    }

    public static ReverseTcpProxyTunnelServer create(Vertx vertx) {
        return new ReverseTcpProxyTunnelServer(vertx, vertx.createNetServer(), SECRET_DEFAULT, generateName());
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


    protected static class DataProxyServer {

        protected final Vertx vertx;
        protected final NetServer netServer;
        protected final String name;
        protected final Handler<NetSocket> connectHandler;

        protected final String host;
        protected final int port;

        public DataProxyServer(Vertx vertx, String name,
                               String host, int port) {
            this.vertx = vertx;
            this.name = name;
            this.host = host;
            this.port = port;
            this.netServer = this.vertx.createNetServer();
            this.connectHandler = socket -> {
            };
        }

        public DataProxyServer(Vertx vertx, String name,
                               int port) {
            this(vertx, name, "0.0.0.0", port);
        }

        public void start() {
            this.netServer
                    .connectHandler(this.connectHandler)
                    .listen(port, host)
                    .onComplete(ar -> {
                        if (ar.succeeded()) {
                            log.info("{} started on {}:{}", name, host, port);
                        } else {
                            Throwable e = ar.cause();
                            log.error("{} start failed", name, e);
                        }
                    });
        }

        public void stop() {
            this.netServer.close()
                    .onSuccess(v -> log.info("{} closed", name))
                    .onFailure(e -> log.error("{} close failed", name, e));
        }

        public boolean startSync() {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean success = new AtomicBoolean(false);
            this.netServer
                    .connectHandler(this.connectHandler)
                    .listen(port, host)
                    .onComplete(ar -> {
                        if (ar.succeeded()) {
                            success.set(true);
                            log.info("{} started on {}:{}", name, host, port);
                        } else {
                            Throwable e = ar.cause();
                            log.error("{} start failed", name, e);
                        }
                        latch.countDown();
                    });

            try {
                latch.await();
            } catch (Exception ignore) {

            }
            return success.get();
        }

        public boolean stopSync() {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean success = new AtomicBoolean(false);
            this.netServer.close()
                    .onComplete(ar -> {
                        if (ar.succeeded()) {
                            success.set(true);
                            log.info("{} closed", name);
                        } else {
                            log.error("{} close failed", name, ar.cause());
                        }
                        latch.countDown();
                    });
            try {
                latch.await();
            } catch (Exception ignore) {

            }
            return success.get();
        }

    }

}

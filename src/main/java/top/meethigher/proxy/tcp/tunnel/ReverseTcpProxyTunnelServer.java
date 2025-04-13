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
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.handler.AbstractTunnelHandler;
import top.meethigher.proxy.tcp.tunnel.handler.TunnelHandler;
import top.meethigher.proxy.tcp.tunnel.proto.TunnelMessage;
import top.meethigher.proxy.tcp.tunnel.utils.IdGenerator;
import top.meethigher.proxy.tcp.tunnel.utils.UserConnection;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一个{@code ReverseTcpProxyTunnelServer}内部有两种服务
 * <ul>
 *     <li>TunnelServer : 即{@code ReverseTcpProxyTunnelServer}本身，作为<b>控制服务</b>，内部维持<b>控制连接</b></li>
 *     <li>DataProxyServer : 作为<b>数据服务</b>，内部维持<b>用户连接</b>和<b>数据连接</b>，数据连接与用户连接绑定双向生命周期、双向数据传输。数据服务生命周期与控制连接进行绑定，<b>一个控制连接，就代表控制一个数据服务</b>。</li>
 * </ul>
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

    protected String host = "0.0.0.0"; // 控制服务监听的主机地址
    protected int port = 44444; // 控制服务监听的端口
    protected int judgeDelay = 30000;// 连接类型的判定延迟，单位毫秒
    protected int heartbeatDelay = 5000;// 毫秒
    protected Map<NetSocket, DataProxyServer> authedSockets = new ConcurrentHashMap<>();// 授权成功的控制连接与数据服务的对应关系

    protected final String secret; // 鉴权密钥
    protected final String name; // 控制服务的名称

    public ReverseTcpProxyTunnelServer(Vertx vertx, NetServer netServer, String secret, String name) {
        super(vertx, netServer);
        this.secret = secret;
        this.name = name;
        addMessageHandler();
    }

    public ReverseTcpProxyTunnelServer port(int port) {
        this.port = port;
        return this;
    }

    public ReverseTcpProxyTunnelServer host(String host) {
        this.host = host;
        return this;
    }

    public ReverseTcpProxyTunnelServer judgeDelay(int judgeDelay) {
        this.judgeDelay = judgeDelay;
        return this;
    }


    public ReverseTcpProxyTunnelServer heartbeatDelay(int heartbeatDelay) {
        this.heartbeatDelay = heartbeatDelay;
        return this;
    }

    /**
     * 控制连接的处理逻辑
     *
     * @param socket 控制连接
     */
    protected void handleConnect(NetSocket socket) {
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
        netServer.connectHandler(this::handleConnect).exceptionHandler(e -> log.error("connect failed", e));
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
        protected final String name; // 数据服务唯一标识，在一个控制服务中，不允许启用同名数据服务
        protected final String host; // 数据服务监听的主机地址
        protected final int port; // 数据服务监听的端口
        protected final NetSocket controlSocket; // 控制连接。数据服务生命周期与控制连接进行绑定
        protected final int judgeDelay;// 连接类型的判定延迟，单位毫秒
        protected final Map<Integer, UserConnection> unboundUserConnections = new ConcurrentHashMap<>();// 等待与数据连接进行配对的用户连接


        public DataProxyServer(Vertx vertx, String name,
                               String host, int port,
                               NetSocket controlSocket,
                               int judgeDelay) {
            this.vertx = vertx;
            this.name = name;
            this.host = host;
            this.port = port;
            this.controlSocket = controlSocket;
            this.judgeDelay = judgeDelay;
            this.netServer = this.vertx.createNetServer();
        }

        public DataProxyServer(Vertx vertx, String name,
                               int port,
                               NetSocket controlSocket,
                               int judgeDelay) {
            this(vertx, name, "0.0.0.0", port, controlSocket, judgeDelay);
        }

        /**
         * 连接有两种，分别为用户连接和数据连接。
         * <p>
         * 用户连接由用户主动发起，对我来说是不可控的。
         * <p>
         * 数据连接由 {@code TunnelClient} 主动发起，对我来说是可控的。
         * <p>
         * 由于无法直接通过TCP连接来判定类型，因此我需要在编写数据连接时，让 {@code TunnelClient}连接 {@code TunnelServer}成功后主动发送一条特定的消息，格式为：
         * <pre>4字节标识码+4字节唯一编号</pre>
         * <p>
         * 通过标识码判定是用户连接还是数据连接，通过唯一编号判定用户连接和数据连接的对应关系
         *
         * @param socket 建立的网络连接Socket，需要通过前缀字节判断其类型（用户连接或数据连接）
         */
        protected void handleConnect(NetSocket socket) {
            socket.pause();
            /**
             * 连接的判定，分为两种情况。
             * 第一种：用户建立连接后，主动发送数据请求，此时直接通过数据包即可判定用户连接还是数据连接。如HTTP
             * 第二种：用户建立连接后，等待服务端主动发送请求，此时就需要使用到延迟判定他是一个数据连接。如SSH
             */
            final long timerId = vertx.setTimer(judgeDelay, id -> handleUserConnection(socket, null, -1));
            // 创建缓冲区
            final Buffer buf = Buffer.buffer();
            socket.handler(buffer -> {
                buf.appendBuffer(buffer);
                if (buf.length() < 8) {
                    return;
                }
                if (buf.getByte(0) == Tunnel.DATA_CONN_FLAG[0]
                        && buf.getByte(1) == Tunnel.DATA_CONN_FLAG[1]
                        && buf.getByte(2) == Tunnel.DATA_CONN_FLAG[2]
                        && buf.getByte(3) == Tunnel.DATA_CONN_FLAG[3]
                ) {
                    handleDataConnection(socket, buf, timerId);
                } else {
                    handleUserConnection(socket, buf, timerId);
                }
            });
            log.debug("{}: connection {} established, is it a data connection or user connection?", name, socket.remoteAddress());
            socket.resume();
        }

        /**
         * 数据连接的处理逻辑
         *
         * @param socket  数据连接的Socket
         * @param buf     接收到的数据缓冲区，包含4字节标识码和4字节会话ID
         * @param timerId 延时判定数据连接的定时器id，-1表示不存在定时器
         */
        protected void handleDataConnection(NetSocket socket, Buffer buf, long timerId) {
            log.debug("{}: oh, connection {} is a data connection!", name, socket.remoteAddress());
            // 取消延迟判定的逻辑
            if (timerId != -1) {
                vertx.cancelTimer(timerId);
            }
            // 数据连接
            int sessionId = buf.getInt(4);
            UserConnection userConn = unboundUserConnections.remove(sessionId);
            if (userConn != null) {
                bindConnections(userConn, socket, sessionId);
            } else {
                log.debug("{}: invalid session id {}, connection {} will be closed", name, sessionId, socket.remoteAddress());
                socket.close();
            }
        }

        /**
         * 用户连接的处理逻辑
         *
         * @param socket  用户连接的Socket
         * @param buf     接收到的数据缓冲区，可能为null表示没有接收到数据
         * @param timerId 延时判定数据连接的定时器id，-1表示不存在定时器
         */
        protected void handleUserConnection(NetSocket socket, Buffer buf, long timerId) {
            log.debug("{}: oh, connection {} is a user connection!", name, socket.remoteAddress());
            // 取消延迟判定的逻辑
            if (timerId != -1) {
                vertx.cancelTimer(timerId);
            }
            // 用户连接
            int sessionId = IdGenerator.nextId();
            UserConnection userConn = new UserConnection(sessionId, socket, new ArrayList<>());
            if (buf != null) {
                userConn.buffers.add(buf.copy());
            }
            unboundUserConnections.put(sessionId, userConn);
            log.debug("{}: user connection {} create session id {}, wait for data connection ...",
                    name, socket.remoteAddress(), sessionId);
            // 通过控制连接通知TunnelClient主动建立数据连接。服务端不需要通知客户端需要连接的端口，因为数据端口的启动是由客户端通知服务端开启的。
            controlSocket.write(TunnelMessageCodec.encode(TunnelMessageType.OPEN_DATA_CONN.code(),
                    TunnelMessage.OpenDataConn.newBuilder().setSessionId(sessionId).build().toByteArray()));
        }

        /**
         * 将用户连接与数据连接进行双向生命周期绑定、双向数据转发
         *
         * @param userConn   用户连接信息，含socket
         * @param dataSocket 数据连接socket
         * @param sessionId  绑定的会话编号
         */
        protected void bindConnections(UserConnection userConn, NetSocket dataSocket, int sessionId) {
            NetSocket userSocket = userConn.netSocket;
            // 双向生命周期绑定、双向数据转发
            userSocket.closeHandler(v -> {
                log.debug("{}: user connection {} closed", name, userSocket.remoteAddress());
                dataSocket.close();
            }).pipeTo(dataSocket).onFailure(e -> {
                log.error("{}: user connection {} pipe to data connection {} failed, connection will be closed",
                        name, userSocket.remoteAddress(), dataSocket.remoteAddress(), e);
                dataSocket.close();
            });
            dataSocket.closeHandler(v -> {
                log.debug("{}: data connection {} closed", name, dataSocket.remoteAddress());
                userSocket.close();
            }).pipeTo(userSocket).onFailure(e -> {
                log.error("{}: data connection {} pipe to user connection {} failed, connection will be closed",
                        name, dataSocket.remoteAddress(), userSocket.remoteAddress(), e);
                userSocket.close();
            });
            // 将用户连接中的缓存数据发出。
            userConn.buffers.forEach(dataSocket::write);
            log.debug("{}: data connection {} bound to user connection {} for session id {}",
                    name,
                    dataSocket.remoteAddress(),
                    userSocket.remoteAddress(),
                    sessionId);
        }

        public void start() {
            this.netServer
                    .connectHandler(this::handleConnect)
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
                    .connectHandler(this::handleConnect)
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
                    builder.setHeartbeatDelay(heartbeatDelay);
                    if (secret.equals(parsed.getSecret())) {
                        synchronized (ReverseTcpProxyTunnelServer.class) {
                            // 判断dataProxyName是否唯一
                            for (DataProxyServer server : authedSockets.values()) {
                                if (server.name.equals(parsed.getDataProxyName())) {
                                    builder.setSuccess(result).setMessage(server.name + " already started");
                                    netSocket.write(encode(TunnelMessageType.OPEN_DATA_PORT_ACK,
                                            builder.build().toByteArray())).onComplete(ar -> netSocket.close());
                                    return result;
                                }
                            }
                            String property = System.getProperty("setDataProxyHost", "false");
                            log.debug("-DsetDataProxyHost: {}", property);
                            final DataProxyServer dataProxyServer;
                            if (Boolean.parseBoolean(property)) {
                                dataProxyServer = new DataProxyServer(vertx,
                                        parsed.getDataProxyName(), parsed.getDataProxyHost(), parsed.getDataProxyPort(),
                                        netSocket, judgeDelay);
                            } else {
                                dataProxyServer = new DataProxyServer(vertx,
                                        parsed.getDataProxyName(),
                                        parsed.getDataProxyPort(),
                                        netSocket, judgeDelay);
                            }
                            log.debug("{} will listen on {}:{}", dataProxyServer.name, dataProxyServer.host, dataProxyServer.port);
                            if (dataProxyServer.startSync()) {
                                result = true;
                                builder.setSuccess(result).setMessage("success");
                                netSocket.write(encode(TunnelMessageType.OPEN_DATA_PORT_ACK,
                                        builder.build().toByteArray()));
                                authedSockets.put(netSocket, dataProxyServer);
                            } else {
                                builder.setSuccess(result).setMessage("failed to open data port " + parsed.getDataProxyPort());
                                netSocket.write(encode(TunnelMessageType.OPEN_DATA_PORT_ACK,
                                        builder.build().toByteArray())).onComplete(ar -> netSocket.close());
                            }
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
                } catch (Exception ignore) {
                }
                return result;
            }
        });

        // 监听数据连接响应事件
        this.on(TunnelMessageType.OPEN_DATA_CONN_ACK, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                boolean result = false;
                try {
                    TunnelMessage.OpenDataConnAck openDataConnAck = TunnelMessage.OpenDataConnAck.parseFrom(bodyBytes);
                    result = openDataConnAck.getSuccess();
                } catch (Exception ignore) {
                }
                return result;
            }
        });
    }
}

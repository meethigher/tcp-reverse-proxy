package top.meethigher.proxy.tcp.tunnel;


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


    protected final Map<NetSocket, DataProxyServer> authedSockets;// 授权成功的控制连接与数据服务的对应关系
    protected final String name; // 控制服务的名称

    protected ReverseTcpProxyTunnelServer(Vertx vertx, NetServer netServer, String secret, Map<NetSocket, DataProxyServer> authedSockets, String name) {
        super(vertx, netServer, secret);
        this.name = name;
        this.authedSockets = authedSockets;
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
            log.debug("{} -- {} closed", socket.remoteAddress(), socket.localAddress());
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


    public static ReverseTcpProxyTunnelServer create(Vertx vertx, NetServer netServer, String secret, Map<NetSocket, DataProxyServer> authedSockets, String name) {
        return new ReverseTcpProxyTunnelServer(vertx, netServer, secret, authedSockets, name);
    }

    public static ReverseTcpProxyTunnelServer create(Vertx vertx, NetServer netServer, String secret, Map<NetSocket, DataProxyServer> authedSockets) {
        return new ReverseTcpProxyTunnelServer(vertx, netServer, secret, authedSockets, generateName());
    }

    public static ReverseTcpProxyTunnelServer create(Vertx vertx, NetServer netServer) {
        return new ReverseTcpProxyTunnelServer(vertx, netServer, SECRET_DEFAULT, new ConcurrentHashMap<>(), generateName());
    }

    public static ReverseTcpProxyTunnelServer create(Vertx vertx) {
        return new ReverseTcpProxyTunnelServer(vertx, vertx.createNetServer(), SECRET_DEFAULT, new ConcurrentHashMap<>(), generateName());
    }


    public static String generateName() {
        final String prefix = ReverseTcpProxyTunnelServer.class.getSimpleName() + "-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger(ReverseTcpProxyTunnelServer.class.getName() + ".name", 0) + 1);
                System.setProperty(ReverseTcpProxyTunnelServer.class.getName() + ".name", next);
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
        netServer.connectHandler(this::handleConnect)
                .exceptionHandler(e -> log.error("{}: socket errors happening before the connection is passed to the connectHandler", name, e))
                .listen(port, host)
                .onFailure(e -> log.error("{} start failed", name, e))
                .onSuccess(v -> log.info("{} started on {}:{}", name, host, port));
    }

    public void stop() {
        netServer.close()
                .onSuccess(v -> log.info("{} closed", name))
                .onFailure(e -> log.error("{} close failed", name, e));
    }


    public static class DataProxyServer {

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
            log.debug("{}: connection {} -- {} established", name, socket.remoteAddress(), socket.localAddress());
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
            // 取消延迟判定的逻辑
            if (timerId != -1) {
                vertx.cancelTimer(timerId);
            }
            // 数据连接
            int sessionId = buf.getInt(4);
            log.debug("{}: sessionId {}, connection {} -- {} is a data connection", name, sessionId, socket.remoteAddress(), socket.localAddress());
            UserConnection userConn = unboundUserConnections.remove(sessionId);
            if (userConn != null) {
                bindConnections(userConn, socket, sessionId);
            } else {
                log.debug("{}: sessionId {}, invalid session id, connection {} -- {} will be closed", name, sessionId, socket.remoteAddress(), socket.localAddress());
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
            // 取消延迟判定的逻辑
            if (timerId != -1) {
                vertx.cancelTimer(timerId);
            }
            // 用户连接
            int sessionId = IdGenerator.nextId();
            log.debug("{}: sessionId {}, connection {} -- {} is a user connection", name, sessionId, socket.remoteAddress(), socket.localAddress());
            UserConnection userConn = new UserConnection(sessionId, socket, new ArrayList<>());
            if (buf != null) {
                userConn.buffers.add(buf.copy());
            }
            unboundUserConnections.put(sessionId, userConn);
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
            // feat: v1.0.5以前的版本，在closeHandler里面，将对端连接也关闭。比如targetSocket关闭时，则将sourceSocket也关闭。
            // 结果导致在转发短连接时，出现了bug。参考https://github.com/meethigher/tcp-reverse-proxy/issues/6
            // 由于内部都是使用pipe来进行数据传输，所以exceptionHandler肯定是都重新注册过了，参考{@code io.vertx.core.streams.impl.PipeImpl.PipeImpl }
            // 但如果还没进入pipe前，连接出现异常，那么就会触发此处的exceptionHandler。https://github.com/meethigher/tcp-reverse-proxy/issues/18
            userSocket.exceptionHandler(e -> log.error("{}: sessionId {}, user connection {} -- {} exception occurred", name, sessionId, userSocket.remoteAddress(), userSocket.localAddress(), e))
                    .closeHandler(v -> log.debug("{}: sessionId {}, user connection {} -- {} closed", name, sessionId, userSocket.remoteAddress(), userSocket.localAddress()))
                    .pipeTo(dataSocket)
                    .onFailure(e -> log.error("{}: sessionId {}, user connection {} -- {} pipe to data connection {} -- {} failed",
                            name,
                            sessionId,
                            userSocket.remoteAddress(), userSocket.localAddress(), dataSocket.remoteAddress(), dataSocket.localAddress(), e))
                    .onSuccess(v -> log.debug("{}: sessionId {}, user connection {} -- {} pipe to data connection {} -- {} succeeded",
                            name,
                            sessionId,
                            userSocket.remoteAddress(), userSocket.localAddress(), dataSocket.remoteAddress(), dataSocket.localAddress()));
            // 由于内部都是使用pipe来进行数据传输，所以exceptionHandler肯定是都重新注册过了，参考{@code io.vertx.core.streams.impl.PipeImpl.PipeImpl }
            // 但如果还没进入pipe前，连接出现异常，那么就会触发此处的exceptionHandler。https://github.com/meethigher/tcp-reverse-proxy/issues/18
            dataSocket.exceptionHandler(e -> log.error("{}: sessionId {}, data connection {} -- {} exception occurred", name, sessionId, dataSocket.remoteAddress(), dataSocket.localAddress(), e))
                    .closeHandler(v -> log.debug("{}: sessionId {}, data connection {} -- {} closed", name, sessionId, dataSocket.remoteAddress(), dataSocket.localAddress()))
                    .pipeTo(userSocket)
                    .onFailure(e -> log.error("{}: sessionId {}, data connection {} -- {} pipe to user connection {} -- {} failed",
                            name,
                            sessionId,
                            dataSocket.remoteAddress(), dataSocket.localAddress(), userSocket.remoteAddress(), userSocket.localAddress(), e))
                    .onSuccess(v -> log.debug("{}: sessionId {}, data connection {} -- {} pipe to user connection {} -- {} succeeded",
                            name,
                            sessionId,
                            dataSocket.remoteAddress(), dataSocket.localAddress(), userSocket.remoteAddress(), userSocket.localAddress()));
            log.debug("{}: sessionId {}, data connection {} -- {} bound to user connection {} -- {} for session id {}",
                    name,
                    sessionId,
                    dataSocket.remoteAddress(), dataSocket.localAddress(),
                    userSocket.remoteAddress(), userSocket.localAddress(),
                    sessionId);
            // 通过数据连接传输"用户连接与数据连接已进行双向数据传输绑定"
            dataSocket.write(Buffer.buffer()
                    .appendBytes(DATA_CONN_FLAG)
                    .appendInt(sessionId)).onSuccess(v -> {
                // 将用户连接中的缓存数据发出。
                userConn.buffers.forEach(b -> dataSocket.write(b)
                        .onSuccess(o -> log.debug("{}: sessionId {}, user connection {} -- {} write to data connection {} -- {} succeeded",
                                name,
                                sessionId,
                                userSocket.remoteAddress(), userSocket.localAddress(), dataSocket.remoteAddress(), dataSocket.localAddress())));
            });

        }

        public void start() {
            this.netServer
                    .connectHandler(this::handleConnect)
                    .exceptionHandler(e -> log.error("{} socket errors happening before the connection is passed to the connectHandler", name, e))
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
                    .exceptionHandler(e -> log.error("{} socket errors happening before the connection is passed to the connectHandler", name, e))
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
        this.onConnected((vertx1, netSocket, buffer) -> log.debug("{} -- {} connected", netSocket.remoteAddress(), netSocket.localAddress()));

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
                TunnelMessage.OpenDataPortAck.Builder builder = TunnelMessage.OpenDataPortAck
                        .newBuilder();
                builder.setHeartbeatDelay(heartbeatDelay);
                try {
                    byte[] data = aesBase64Decode(bodyBytes);
                    if (data == null) {
                        builder.setSuccess(result).setMessage("your secret is incorrect!");
                        netSocket.write(encode(TunnelMessageType.OPEN_DATA_PORT_ACK,
                                builder.build().toByteArray())).onComplete(ar -> netSocket.close());
                        return result;
                    }
                    TunnelMessage.OpenDataPort parsed = TunnelMessage.OpenDataPort.parseFrom(data);
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
                        builder.setSuccess(result).setMessage("your secret is incorrect!");
                        netSocket.write(encode(TunnelMessageType.OPEN_DATA_PORT_ACK,
                                builder.build().toByteArray())).onComplete(ar -> netSocket.close());
                    }
                } catch (Exception e) {
                    log.error("open data port doHandle occurred exception", e);
                    builder.setSuccess(result).setMessage("exception");
                    netSocket.write(encode(TunnelMessageType.OPEN_DATA_PORT_ACK,
                            builder.build().toByteArray())).onComplete(ar -> netSocket.close());
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
                    TunnelMessage.OpenDataConnAck openDataConnAck = TunnelMessage.OpenDataConnAck.parseFrom(aesBase64Decode(bodyBytes));
                    result = openDataConnAck.getSuccess();
                } catch (Exception ignore) {
                }
                return result;
            }
        });
    }
}

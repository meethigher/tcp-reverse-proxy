package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.handler.AbstractTunnelHandler;
import top.meethigher.proxy.tcp.tunnel.proto.TunnelMessage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一个{@code ReverseTcpProxyTunnelClient}内部维护不同的连接，分别为
 * <ul>
 * <li>一个控制连接，与{@code TunnelServer}通信，支持失败重连</li>
 * <li>多个数据连接，与{@code DataProxyServer}通信</li>
 * <li>多个后端连接，与你实际要内网穿透出去的服务通信</li>
 * </ul>
 * 其中，数据连接与后端连接绑定双向生命周期、双向数据传输。
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
    protected static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    protected static final long MIN_DELAY_DEFAULT = 1000;// 毫秒
    protected static final long MAX_DELAY_DEFAULT = 64000;// 毫秒


    protected final String name;

    protected long heartbeatDelay;
    protected String backendHost = "meethigher.top";
    protected int backendPort = 22;
    protected String dataProxyHost = "127.0.0.1";
    protected int dataProxyPort = 22;
    protected String dataProxyName = "ssh-proxy";


    public static String generateName() {
        final String prefix = ReverseTcpProxyTunnelClient.class.getSimpleName() + "-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger(ReverseTcpProxyTunnelClient.class.getName() + ".name", 0) + 1);
                System.setProperty(ReverseTcpProxyTunnelClient.class.getName() + ".name", next);
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
                                          long minDelay, long maxDelay,
                                          String secret, String name) {
        super(vertx, netClient, minDelay, maxDelay, secret);
        this.name = name;
        addMessageHandler();
    }

    public ReverseTcpProxyTunnelClient backendHost(String backendHost) {
        this.backendHost = backendHost;
        return this;
    }

    public ReverseTcpProxyTunnelClient backendPort(int backendPort) {
        this.backendPort = backendPort;
        return this;
    }

    public ReverseTcpProxyTunnelClient dataProxyHost(String dataProxyHost) {
        this.dataProxyHost = dataProxyHost;
        return this;
    }

    public ReverseTcpProxyTunnelClient dataProxyPort(int dataProxyPort) {
        this.dataProxyPort = dataProxyPort;
        return this;
    }

    public ReverseTcpProxyTunnelClient dataProxyName(String dataProxyName) {
        this.dataProxyName = dataProxyName;
        super.name = this.dataProxyName;
        return this;
    }

    public static ReverseTcpProxyTunnelClient create(Vertx vertx, NetClient netClient, long minDelay, long maxDelay, String secret, String name) {
        return new ReverseTcpProxyTunnelClient(vertx, netClient, minDelay, maxDelay, secret, name);
    }

    public static ReverseTcpProxyTunnelClient create(Vertx vertx, NetClient netClient, long minDelay, long maxDelay, String secret) {
        return new ReverseTcpProxyTunnelClient(vertx, netClient, minDelay, maxDelay, secret, generateName());
    }

    public static ReverseTcpProxyTunnelClient create(Vertx vertx, NetClient netClient, String secret) {
        return new ReverseTcpProxyTunnelClient(vertx, netClient, MIN_DELAY_DEFAULT, MAX_DELAY_DEFAULT, secret, generateName());
    }


    public static ReverseTcpProxyTunnelClient create(Vertx vertx, NetClient netClient) {
        return new ReverseTcpProxyTunnelClient(vertx, netClient, MIN_DELAY_DEFAULT, MAX_DELAY_DEFAULT, SECRET_DEFAULT, generateName());
    }

    public static ReverseTcpProxyTunnelClient create(Vertx vertx) {
        return new ReverseTcpProxyTunnelClient(vertx, vertx.createNetClient(), MIN_DELAY_DEFAULT, MAX_DELAY_DEFAULT, SECRET_DEFAULT, generateName());
    }


    /**
     * 注册内网穿透的监听逻辑
     */
    protected void addMessageHandler() {
        // 监听连接成功事件
        this.onConnected((vertx, netSocket, buffer) -> netSocket.write(encode(TunnelMessageType.OPEN_DATA_PORT,
                TunnelMessage.OpenDataPort.newBuilder()
                        .setSecret(secret)
                        .setDataProxyHost(dataProxyHost)
                        .setDataProxyPort(dataProxyPort)
                        .setDataProxyName(dataProxyName)
                        .build().toByteArray())));

        // 监听授权与开通数据端口事件
        this.on(TunnelMessageType.OPEN_DATA_PORT_ACK, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                boolean result = false;
                try {
                    TunnelMessage.OpenDataPortAck parsed = TunnelMessage.OpenDataPortAck.parseFrom(aesBase64Decode(bodyBytes));
                    if (parsed.getSuccess()) {
                        // 如果认证 + 开通端口成功，那么就需要进行长连接保持，并开启定期心跳。
                        result = true;
                        heartbeatDelay = parsed.getHeartbeatDelay();
                        vertx.setTimer(heartbeatDelay, id -> netSocket.write(encode(TunnelMessageType.HEARTBEAT,
                                TunnelMessage.Heartbeat.newBuilder().setTimestamp(System.currentTimeMillis()).build().toByteArray())));
                    } else {
                        // 如果认证失败，服务端会主动关闭 tcp 连接
                        log.warn("message type {} : {}", TunnelMessageType.OPEN_DATA_PORT_ACK, parsed.getMessage());
                    }
                } catch (Exception e) {
                }
                return result;
            }
        });

        // 监听心跳事件
        this.on(TunnelMessageType.HEARTBEAT_ACK, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                // 只要收到心跳，就证明没问题。不用去解析内容。直接开启下一波心跳计划即可。
                vertx.setTimer(heartbeatDelay, id -> netSocket.write(encode(TunnelMessageType.HEARTBEAT,
                        TunnelMessage.Heartbeat.newBuilder().setTimestamp(System.currentTimeMillis()).build().toByteArray())));
                return true;
            }
        });

        // 监听数据连接事件
        this.on(TunnelMessageType.OPEN_DATA_CONN, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                final AtomicBoolean atomicResult = new AtomicBoolean(false);
                try {
                    TunnelMessage.OpenDataConn parsed = TunnelMessage.OpenDataConn.parseFrom(bodyBytes);
                    final int sessionId = parsed.getSessionId();
                    // 保证顺序，并将建立数据连接的逻辑返回给控制连接。
                    CountDownLatch latch = new CountDownLatch(1);
                    // 建立数据连接
                    Handler<Throwable> dataSocketFailureHandler = e -> {
                        log.error("{}: sessionId {}, client failed to open data connection {}:{}",
                                dataProxyName,
                                sessionId,
                                dataProxyHost,
                                dataProxyPort);
                        latch.countDown();
                    };
                    Handler<NetSocket> dataSocketSuccessHandler = dataSocket -> {
                        atomicResult.set(true);
                        latch.countDown();
                        dataSocket.pause();
                        log.debug("{}: sessionId {}, data connection {} -- {} established. ",
                                dataProxyName,
                                sessionId,
                                dataSocket.remoteAddress(), dataSocket.localAddress());
                        // 连接建立成功后，立马发送消息告诉数据服务"我是数据连接"
                        dataSocket.write(Buffer.buffer()
                                .appendBytes(DATA_CONN_FLAG)
                                .appendInt(sessionId));
                        final Buffer buf = Buffer.buffer();
                        // 等待数据连接返回与用户连接的绑定结果
                        dataSocket.handler(buffer -> {
                            buf.appendBuffer(buffer);
                            if (buf.length() < 8) {
                                return;
                            }
                            // note: 前8个字节是tunnel通信使用的。
                            if (buf.getByte(0) == Tunnel.DATA_CONN_FLAG[0]
                                    && buf.getByte(1) == Tunnel.DATA_CONN_FLAG[1]
                                    && buf.getByte(2) == Tunnel.DATA_CONN_FLAG[2]
                                    && buf.getByte(3) == Tunnel.DATA_CONN_FLAG[3]
                                    && buf.getInt(4) == sessionId
                            ) {
                                // 用户连接已成功与数据连接绑定。开始建立后端连接
                                dataSocket.pause();
                                netClient.connect(backendPort, backendHost)
                                        .onFailure(e -> {
                                            log.error("{}: sessionId {}, client open backend connection to {}:{} failed",
                                                    dataProxyName,
                                                    sessionId,
                                                    backendHost, backendPort, e);
                                            dataSocket.close();
                                        })
                                        .onSuccess(backendSocket -> {
                                            backendSocket.pause();
                                            // 若实际数据传输的长度大于8字节，那么后面的字节需要发出去。
                                            // https://github.com/meethigher/tcp-reverse-proxy/issues/9
                                            if (buf.length() > 8) {
                                                backendSocket.write(buf.getBuffer(8, buf.length()))
                                                        .onSuccess(o -> log.debug("{}: sessionId {}, data connection {} -- {} write to backend connection {} -- {} succeeded",
                                                                dataProxyName,
                                                                sessionId,
                                                                dataSocket.remoteAddress(), dataSocket.localAddress(),
                                                                backendSocket.remoteAddress(), backendSocket.localAddress()));
                                            }
                                            log.debug("{}: sessionId {}, backend connection {} -- {} established", dataProxyName, sessionId, backendSocket.remoteAddress(), backendSocket.localAddress());
                                            // 双向生命周期绑定、双向数据转发
                                            // feat: v1.0.5以前的版本，在closeHandler里面，将对端连接也关闭。比如targetSocket关闭时，则将sourceSocket也关闭。
                                            // 结果导致在转发短连接时，出现了bug。参考https://github.com/meethigher/tcp-reverse-proxy/issues/6
                                            dataSocket.closeHandler(v -> log.debug("{}: sessionId {}, data connection {} -- {} closed", dataProxyName, sessionId, dataSocket.remoteAddress(), dataSocket.localAddress()))
                                                    .pipeTo(backendSocket)
                                                    .onFailure(e -> log.error("{}: sessionId {}, data connection {} -- {} pipe to backend connection {} -- {} failed",
                                                            dataProxyName,
                                                            sessionId,
                                                            dataSocket.remoteAddress(), dataSocket.localAddress(),
                                                            backendSocket.remoteAddress(), backendSocket.localAddress(),
                                                            e))
                                                    .onSuccess(v -> log.debug("{}: sessionId {}, data connection {} -- {} pipe to backend connection {} -- {} succeeded",
                                                            dataProxyName,
                                                            sessionId,
                                                            dataSocket.remoteAddress(), dataSocket.localAddress(),
                                                            backendSocket.remoteAddress(), backendSocket.localAddress()));
                                            backendSocket.closeHandler(v -> log.debug("{}: sessionId {}, backend connection {} -- {} closed", dataProxyName, sessionId, backendSocket.remoteAddress(), backendSocket.localAddress()))
                                                    .pipeTo(dataSocket)
                                                    .onFailure(e -> log.error("{}: sessionId {}, backend connection {} -- {} pipe to data connection {} -- {} failed",
                                                            dataProxyName,
                                                            sessionId,
                                                            backendSocket.remoteAddress(), backendSocket.localAddress(),
                                                            dataSocket.remoteAddress(), dataSocket.localAddress(),
                                                            e))
                                                    .onSuccess(v -> log.debug("{}: sessionId {}, backend connection {} -- {} pipe to data connection {} -- {} succeeded",
                                                            dataProxyName,
                                                            sessionId,
                                                            backendSocket.remoteAddress(), backendSocket.localAddress(),
                                                            dataSocket.remoteAddress(), dataSocket.localAddress()));
                                            backendSocket.resume();
                                            dataSocket.resume();
                                            log.debug("{}: sessionId {}, data connection {} -- {} bound to backend connection {} -- {} for session id {}",
                                                    dataProxyName,
                                                    sessionId,
                                                    dataSocket.remoteAddress(), dataSocket.localAddress(),
                                                    backendSocket.remoteAddress(), backendSocket.localAddress(),
                                                    sessionId);
                                        });

                            } else {
                                dataSocket.close();
                                log.warn("{}: sessionId {}, data connection {} -- {} received invalid message, will be closed. ",
                                        dataProxyName,
                                        sessionId,
                                        dataSocket.remoteAddress(), dataSocket.localAddress());
                            }
                        });
                        dataSocket.resume();
                    };
                    netClient.connect(dataProxyPort, dataProxyHost)
                            .onFailure(dataSocketFailureHandler)
                            .onSuccess(dataSocketSuccessHandler);
                    latch.await();
                } catch (Exception ignore) {
                }
                netSocket.write(encode(TunnelMessageType.OPEN_DATA_CONN_ACK, TunnelMessage.OpenDataConnAck.newBuilder()
                        .setSuccess(atomicResult.get()).setMessage("").build().toByteArray()));
                return atomicResult.get();
            }
        });
    }

}

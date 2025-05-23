package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.AsyncResult;
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


    protected final String secret;
    protected final String name;

    protected long heartbeatDelay;
    protected String backendHost = "meethigher.top";
    protected int backendPort = 22;
    protected String dataProxyHost = "127.0.0.1";
    protected int dataProxyPort = 22;
    protected String dataProxyName = "ssh-proxy";


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
                                          long minDelay, long maxDelay,
                                          String secret, String name) {
        super(vertx, netClient, minDelay, maxDelay);
        this.secret = secret;
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
                    TunnelMessage.OpenDataPortAck parsed = TunnelMessage.OpenDataPortAck.parseFrom(bodyBytes);
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
                    // 保证顺序执行。
                    CountDownLatch latch = new CountDownLatch(1);
                    Handler<AsyncResult<NetSocket>> asyncResultHandler = ar -> {
                        if (ar.succeeded()) {
                            final NetSocket dataSocket = ar.result();
                            dataSocket.pause();
                            // 连接建立成功后，立马发送消息告诉数据服务，我是数据连接，并与用户连接进行绑定
                            dataSocket.write(Buffer.buffer()
                                    .appendBytes(DATA_CONN_FLAG)
                                    .appendInt(sessionId));
                            log.debug("{}: sessionId {}, data connection {} -- {} established. wait for backend connection",
                                    dataProxyName,
                                    sessionId,
                                    dataSocket.remoteAddress(), dataSocket.localAddress());
                            netClient.connect(backendPort, backendHost).onComplete(rst -> {
                                if (rst.succeeded()) {
                                    atomicResult.set(rst.succeeded());
                                    final NetSocket backendSocket = rst.result();
                                    backendSocket.pause();
                                    log.debug("{}: sessionId {}, backend connection {} -- {} established", dataProxyName, sessionId, backendSocket.remoteAddress(), backendSocket.localAddress());
                                    // 双向生命周期绑定、双向数据转发
                                    // feat: v1.0.5以前的版本，在closeHandler里面，将对端连接也关闭。比如targetSocket关闭时，则将sourceSocket也关闭。
                                    // 结果导致在转发短连接时，出现了bug。参考https://github.com/meethigher/tcp-reverse-proxy/issues/6
                                    dataSocket.closeHandler(v -> {
                                        log.debug("{}: sessionId {}, data connection {} -- {} closed", dataProxyName, sessionId, dataSocket.remoteAddress(), dataSocket.localAddress());
                                    }).pipeTo(backendSocket).onFailure(e -> {
                                        log.error("{}: sessionId {}, data connection {} -- {} pipe to backend connection {} -- {} failed",
                                                dataProxyName,
                                                sessionId,
                                                dataSocket.remoteAddress(), dataSocket.localAddress(),
                                                backendSocket.remoteAddress(), backendSocket.localAddress(),
                                                e);
                                    });
                                    backendSocket.closeHandler(v -> {
                                        log.debug("{}: sessionId {}, backend connection {} -- {} closed", dataProxyName, sessionId, backendSocket.remoteAddress(), backendSocket.localAddress());
                                    }).pipeTo(dataSocket).onFailure(e -> {
                                        log.error("{}: sessionId {}, backend connection {} -- {} pipe to data connection {} -- {} failed",
                                                dataProxyName,
                                                sessionId,
                                                backendSocket.remoteAddress(), backendSocket.localAddress(),
                                                dataSocket.remoteAddress(), dataSocket.localAddress(),
                                                e);
                                    });
                                    backendSocket.resume();
                                    dataSocket.resume();
                                    log.debug("{}: sessionId {}, data connection {} -- {} bound to backend connection {} -- {} for session id {}",
                                            dataProxyName,
                                            sessionId,
                                            dataSocket.remoteAddress(), dataSocket.localAddress(),
                                            backendSocket.remoteAddress(), backendSocket.localAddress(),
                                            sessionId);
                                } else {
                                    // 建立连接失败，那么数据连接就要关闭
                                    dataSocket.close();
                                    log.error("{}: sessionId {}, client open backend connection to {}:{} failed",
                                            dataProxyName,
                                            sessionId,
                                            backendHost, backendPort, rst.cause());
                                }
                                latch.countDown();
                            });
                        } else {
                            log.error("{}: sessionId {}, client open data connection to {}:{} failed", dataProxyName,
                                    sessionId,
                                    dataProxyHost, dataProxyPort, ar.cause());
                            latch.countDown();
                        }

                    };
                    netClient.connect(dataProxyPort, dataProxyHost).onComplete(asyncResultHandler);
                    latch.await();
                    netSocket.write(encode(TunnelMessageType.OPEN_DATA_CONN_ACK, TunnelMessage.OpenDataConnAck.newBuilder()
                            .setSuccess(atomicResult.get()).setMessage("").build().toByteArray()));
                } catch (Exception ignore) {
                }
                return atomicResult.get();
            }
        });
    }

}

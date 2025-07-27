package top.meethigher.proxy.tcp.mux;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.NetAddress;

import java.util.concurrent.ThreadLocalRandom;

/**
 * ReverseTcpProxyMuxServer
 * <p>
 * 搭配{@code top.meethigher.proxy.tcp.mux.ReverseTcpProxyMuxClient }实现单端口多路复用
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc1078">RFC 1078 - TCP port service Multiplexer (TCPMUX)</a>
 * @since 2025/07/26 20:41
 */
public class ReverseTcpProxyMuxServer extends Mux {

    private static final Logger log = LoggerFactory.getLogger(ReverseTcpProxyMuxServer.class);
    protected final NetServer netServer;
    protected final NetClient netClient;
    protected final String name;

    protected String host = "0.0.0.0";
    protected int port = 997;

    protected ReverseTcpProxyMuxServer(Vertx vertx, String secret, NetServer netServer, NetClient netClient, String name) {
        super(vertx, secret);
        this.netServer = netServer;
        this.netClient = netClient;
        this.name = name;
    }

    protected void handleConnect(NetSocket src) {
        src.pause();
        log.debug("source {} -- {} connected", src.localAddress(), src.remoteAddress());
        // 由于内部都是使用pipe来进行数据传输，所以exceptionHandler肯定是都重新注册过了，参考{@code io.vertx.core.streams.impl.PipeImpl.PipeImpl }
        // 但如果还没进入pipe前，连接出现异常，那么就会触发此处的exceptionHandler。https://github.com/meethigher/tcp-reverse-proxy/issues/18
        src.exceptionHandler(e -> log.error("source {} -- {} exception occurred", src.localAddress(), src.remoteAddress(), e))
                .closeHandler(v -> log.debug("source {} -- {} closed", src.localAddress(), src.remoteAddress()));
        src.handler(new MuxMessageParser(muxMsg -> this.bindMuxConnections(src, muxMsg), src));
        src.resume();
    }

    /**
     * 根据{@code MuxMessage }建立后端连接，并将数据连接和后端连接进行绑定
     */
    protected void bindMuxConnections(NetSocket src, MuxMessageParser.MuxMessage muxMsg) {
        src.pause();
        NetAddress backend = aesBase64Decode(muxMsg.backendServerBuf);
        if (backend == null) {
            log.warn("source {} -- {} exception occurred: failed to parsing the backendServer address from encrypted content:{}",
                    src.localAddress(), src.remoteAddress(),
                    muxMsg.backendServerBuf);
            src.close();
            return;
        }
        netClient.connect(backend.getPort(), backend.getHost())
                .onFailure(e -> {
                    log.error("source {} -- {} failed to connect to {}", src.localAddress(), src.remoteAddress(), backend, e);
                    src.close();
                })
                .onSuccess(dst -> {
                    dst.pause();
                    log.debug("target {} -- {} connected", dst.localAddress(), dst.remoteAddress());
                    // 由于内部都是使用pipe来进行数据传输，所以exceptionHandler肯定是都重新注册过了，参考{@code io.vertx.core.streams.impl.PipeImpl.PipeImpl }
                    // 但如果还没进入pipe前，连接出现异常，那么就会触发此处的exceptionHandler。https://github.com/meethigher/tcp-reverse-proxy/issues/18
                    dst.exceptionHandler(e -> log.error("target {} -- {} exception occurred", dst.localAddress(), dst.remoteAddress(), e))
                            .closeHandler(v -> log.debug("target {} -- {} closed", dst.localAddress(), dst.remoteAddress()));
                    /**
                     * 不能使用write的成功与否判断链路是否正常，但是可以通过write.onSuccess保证顺序写入。
                     * 测试中发现，即便链路异常，返回仍然是true 参考 https://github.com/meethigher/bug-test/blob/vertx-network-disconnect/src/main/java/top/meethigher/BugTest.java
                     * write是把数据复制到缓冲区，缓冲区有空间一般就不会失败
                     * 本地测试示例 top.meethigher.proxy.tcp.NetSocketWriteFailureTest
                     */
                    dst.write(muxMsg.payload)
                            .onSuccess(t -> {
                                // https://github.com/meethigher/tcp-reverse-proxy/issues/12
                                // 将日志记录详细，便于排查问题
                                src.pipeTo(dst)
                                        .onSuccess(v -> log.debug("source {} -- {} pipe to target {} -- {} succeeded",
                                                src.localAddress(), src.remoteAddress(), dst.localAddress(), dst.remoteAddress()))
                                        .onFailure(e -> log.error("source {} -- {} pipe to target {} -- {} failed",
                                                src.localAddress(), src.remoteAddress(), dst.localAddress(), dst.remoteAddress(), e));
                                dst.pipeTo(src)
                                        .onSuccess(v -> log.debug("target {} -- {} pipe to source {} -- {} succeeded",
                                                dst.localAddress(), dst.remoteAddress(), src.localAddress(), src.remoteAddress()))
                                        .onFailure(e -> log.error("target {} -- {} pipe to source {} -- {} failed",
                                                dst.localAddress(), dst.remoteAddress(), src.localAddress(), src.remoteAddress(), e));
                                log.debug("source {} -- {} bound to target {} -- {}", src.localAddress(), src.remoteAddress(),
                                        dst.localAddress(), dst.remoteAddress());
                                src.resume();
                                dst.resume();
                            });
                });

    }

    public static String generateName() {
        final String prefix = ReverseTcpProxyMuxServer.class.getSimpleName() + "-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger(ReverseTcpProxyMuxServer.class.getName() + ".name", 0) + 1);
                System.setProperty(ReverseTcpProxyMuxServer.class.getName() + ".name", next);
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

    public ReverseTcpProxyMuxServer host(String host) {
        this.host = host;
        return this;
    }

    public ReverseTcpProxyMuxServer port(int port) {
        this.port = port;
        return this;
    }

    public void start() {
        netServer.connectHandler(this::handleConnect)
                .exceptionHandler(e -> log.error("{} socket errors happening before the connection is passed to the connectHandler", name, e))
                .listen(port, host)
                .onFailure(e -> log.error("{} start failed", name))
                .onSuccess(v -> log.info("{} started on {}:{}", name, host, port));
    }

    public void stop() {
        netServer.close()
                .onSuccess(v -> log.info("{} closed", name))
                .onFailure(e -> log.error("{} close failed", name, e));

    }

    public static ReverseTcpProxyMuxServer create(Vertx vertx, String secret, NetServer netServer, NetClient netClient, String name) {
        return new ReverseTcpProxyMuxServer(vertx, secret, netServer, netClient, name);
    }

    public static ReverseTcpProxyMuxServer create(Vertx vertx, String secret, NetServer netServer, NetClient netClient) {
        return create(vertx, secret, netServer, netClient, generateName());
    }

    public static ReverseTcpProxyMuxServer create(Vertx vertx, String secret) {
        return create(vertx, secret, vertx.createNetServer(), vertx.createNetClient(), generateName());
    }

    public static ReverseTcpProxyMuxServer create(Vertx vertx) {
        return create(vertx, SECRET_DEFAULT, vertx.createNetServer(), vertx.createNetClient(), generateName());
    }


}

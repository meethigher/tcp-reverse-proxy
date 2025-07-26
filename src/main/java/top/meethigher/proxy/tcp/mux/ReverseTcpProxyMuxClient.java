package top.meethigher.proxy.tcp.mux;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.NetAddress;
import top.meethigher.proxy.tcp.mux.model.MuxNetAddress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ReverseTcpProxyMuxClient
 * <p>
 * 本地启动多个端口，以{@code top.meethigher.proxy.tcp.mux.ReverseTcpProxyMuxServer }一个固定端口为内网的流量出入口，进而实现本地不通端口转发不同的内网服务功能
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc1078">RFC 1078 - TCP port service Multiplexer (TCPMUX)</a>
 * @since 2025/07/26 20:43
 */
public class ReverseTcpProxyMuxClient extends Mux {

    private static final Logger log = LoggerFactory.getLogger(ReverseTcpProxyMuxClient.class);
    /**
     * 端口映射关系<p>
     * key: 本地监听的TCP服务<p>
     * value: 经过 {@code top.meethigher.proxy.tcp.mux.ReverseTcpProxyMuxServer }转发的内网服务
     */
    protected final Map<MuxNetAddress, NetAddress> mapper;

    protected final NetServerOptions netServerOptions;

    protected final NetClient netClient;

    protected final NetAddress muxServer;

    protected final String name;

    protected final List<NetServer> netServers = new ArrayList<>();

    public ReverseTcpProxyMuxClient(Vertx vertx, String secret, Map<MuxNetAddress, NetAddress> mapper, NetServerOptions netServerOptions, NetClient netClient, NetAddress muxServer, String name) {
        super(vertx, secret);
        this.mapper = mapper;
        this.netServerOptions = netServerOptions;
        this.netClient = netClient;
        this.muxServer = muxServer;
        this.name = name;
    }


    protected void handleConnect(NetSocket src, MuxNetAddress localServer, NetAddress backendServer) {
        src.pause();
        log.debug("{}: source {} -- {} connected", localServer.getName(), src.localAddress(), src.remoteAddress());
        src.exceptionHandler(e -> log.error("{}: source {} -- {} exception occurred", localServer.getName(), src.localAddress(), src.remoteAddress(), e))
                .closeHandler(v -> log.debug("{}: source {} -- {} closed", localServer.getName(), src.localAddress(), src.remoteAddress()));
        netClient.connect(muxServer.getPort(), muxServer.getHost())
                .onFailure(e -> {
                    log.error("{}: failed to connect to {}", localServer.getName(), muxServer, e);
                    src.close();
                })
                .onSuccess(dst -> {
                    dst.pause();
                    log.debug("{}: target {} -- {} connected", localServer.getName(), dst.localAddress(), dst.remoteAddress());
                    dst.exceptionHandler(e -> log.error("{}: target {} -- {} exception occurred", localServer.getName(), dst.localAddress(), dst.remoteAddress(), e))
                            .closeHandler(v -> log.debug("{}: target {} -- {} closed", localServer.getName(), dst.localAddress(), dst.remoteAddress()));
                    Handler<Void> writeSuccessHandler = t -> {
                        // https://github.com/meethigher/tcp-reverse-proxy/issues/12
                        // 将日志记录详细，便于排查问题
                        src.pipeTo(dst)
                                .onSuccess(v -> log.debug("{}: source {} -- {} pipe to target {} -- {} succeeded",
                                        localServer.getName(), src.localAddress(), src.remoteAddress(), dst.localAddress(), dst.remoteAddress()))
                                .onFailure(e -> log.error("{}: source {} -- {} pipe to target {} -- {} failed",
                                        localServer.getName(), src.localAddress(), src.remoteAddress(), dst.localAddress(), dst.remoteAddress(), e));
                        dst.pipeTo(src)
                                .onSuccess(v -> log.debug("{}: target {} -- {} pipe to source {} -- {} succeeded",
                                        localServer.getName(), dst.localAddress(), dst.remoteAddress(), src.localAddress(), src.remoteAddress()))
                                .onFailure(e -> log.error("{}: target {} -- {} pipe to source {} -- {} failed",
                                        localServer.getName(), dst.localAddress(), dst.remoteAddress(), src.localAddress(), src.remoteAddress(), e));
                        log.debug("{}: source {} -- {} bound to target {} -- {} with backend server {}",
                                localServer.getName(),
                                src.localAddress(), src.remoteAddress(),
                                dst.localAddress(), dst.remoteAddress(),
                                backendServer);
                        src.resume();
                        dst.resume();
                    };
                    dst.write(this.aesBase64Encode(backendServer))
                            .onSuccess(writeSuccessHandler)
                            .onFailure(e -> {
                                dst.close();
                                src.close();
                            });
                });
    }

    public void start() {
        for (MuxNetAddress local : mapper.keySet()) {
            vertx.createNetServer(netServerOptions)
                    .connectHandler(src ->
                            this.handleConnect(src, local, mapper.get(local)))
                    .exceptionHandler(e -> log.error("{} {} socket errors happening before the connection is passed to the connectHandler", name, local.getName(), e))
                    .listen(local.getPort(), local.getHost())
                    .onFailure(e -> log.error("{} {} start failed on {}", name, local.getName(), local, e))
                    .onSuccess(v -> {
                        log.info("{} {} started on {}. mux server {}, backend server {}",
                                name,
                                local.getName(),
                                local,
                                muxServer,
                                mapper.get(local)
                        );
                        netServers.add(v);
                    });
        }
    }

    public void stop() {
        for (NetServer netServer : netServers) {
            netServer.close()
                    .onSuccess(v -> log.info("{} port {} closed", name, netServer.actualPort()))
                    .onFailure(e -> log.error("{} port {} close failed", name, netServer.actualPort(), e));
        }
        netServers.clear();
    }

    public static String generateName() {
        final String prefix = ReverseTcpProxyMuxClient.class.getSimpleName() + "-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger(ReverseTcpProxyMuxClient.class.getName() + ".name", 0) + 1);
                System.setProperty(ReverseTcpProxyMuxClient.class.getName() + ".name", next);
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

    public static ReverseTcpProxyMuxClient create() {
        Vertx vertx = Vertx.vertx();
        return new ReverseTcpProxyMuxClient(vertx, Mux.SECRET_DEFAULT,
                new HashMap<MuxNetAddress, NetAddress>() {{
                    put(new MuxNetAddress("0.0.0.0", 6666, "ssh1"),
                            new NetAddress("127.0.0.1", 22));
                    put(new MuxNetAddress("0.0.0.0", 6667, "ssh2"),
                            new NetAddress("127.0.0.1", 22));

                }}, new NetServerOptions(), vertx.createNetClient(),
                new NetAddress("10.0.0.30", 22),
                generateName());
    }


}

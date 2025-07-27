package top.meethigher.proxy.tcp;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.LoadBalancer;
import top.meethigher.proxy.NetAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于Vert.x实现的TCP反向代理
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2024/10/30 23:06
 */
public class ReverseTcpProxy {

    private static final Logger log = LoggerFactory.getLogger(ReverseTcpProxy.class);

    protected static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    protected String sourceHost = "0.0.0.0";

    protected int sourcePort = 999;

    protected final Handler<NetSocket> connectHandler;
    protected final NetServer netServer;
    protected final NetClient netClient;
    protected final LoadBalancer<NetAddress> lb;
    protected final List<NetAddress> netAddresses;
    protected final String name;

    protected ReverseTcpProxy(NetServer netServer, NetClient netClient,
                              LoadBalancer<NetAddress> loadBalancer,
                              List<NetAddress> netAddresses,
                              String name) {
        this.name = name;
        this.lb = loadBalancer;
        this.netAddresses = netAddresses;
        this.netServer = netServer;
        this.netClient = netClient;
        this.connectHandler = sourceSocket -> {
            // 暂停流读取
            sourceSocket.pause();
            SocketAddress sourceRemote = sourceSocket.remoteAddress();
            SocketAddress sourceLocal = sourceSocket.localAddress();
            // 由于内部都是使用pipe来进行数据传输，所以exceptionHandler肯定是都重新注册过了，参考{@code io.vertx.core.streams.impl.PipeImpl.PipeImpl }
            // 但如果还没进入pipe前，连接出现异常，那么就会触发此处的exceptionHandler。https://github.com/meethigher/tcp-reverse-proxy/issues/18
            sourceSocket.exceptionHandler(e -> log.error("source {} -- {}  exception occurred", sourceLocal, sourceRemote, e))
                    .closeHandler(v -> log.debug("source {} -- {} closed", sourceLocal, sourceRemote));
            NetAddress next = lb.next();
            String targetHost = next.getHost();
            int targetPort = next.getPort();
            log.debug("source {} -- {} connected. lb [{}] next target {}", sourceLocal, sourceRemote,
                    lb.name(),
                    next
            );
            netClient.connect(targetPort, targetHost)
                    .onFailure(e -> {
                        log.error("source {} -- {} failed to connect to {}:{}", sourceLocal, sourceRemote, targetHost, targetPort, e);
                        // 若连接目标服务失败，需要断开源头服务
                        sourceSocket.close();
                    })
                    .onSuccess(targetSocket -> {
                        targetSocket.pause();
                        SocketAddress targetRemote = targetSocket.remoteAddress();
                        SocketAddress targetLocal = targetSocket.localAddress();
                        log.debug("target {} -- {} connected", targetLocal, targetRemote);

                        // feat: v1.0.5以前的版本，在closeHandler里面，将对端连接也关闭。比如targetSocket关闭时，则将sourceSocket也关闭。
                        // 结果导致在转发短连接时，出现了bug。参考https://github.com/meethigher/tcp-reverse-proxy/issues/6
                        // 由于内部都是使用pipe来进行数据传输，所以exceptionHandler肯定是都重新注册过了，参考{@code io.vertx.core.streams.impl.PipeImpl.PipeImpl }
                        // 但如果还没进入pipe前，连接出现异常，那么就会触发此处的exceptionHandler。https://github.com/meethigher/tcp-reverse-proxy/issues/18
                        targetSocket.exceptionHandler(e -> log.error("target {} -- {}  exception occurred", targetLocal, targetRemote, e))
                                .closeHandler(v -> log.debug("target {} -- {} closed", targetLocal, targetRemote));

                        // https://github.com/meethigher/tcp-reverse-proxy/issues/12
                        // 将日志记录详细，便于排查问题
                        sourceSocket.pipeTo(targetSocket)
                                .onSuccess(v -> log.debug("source {} -- {} pipe to target {} -- {} succeeded",
                                        sourceLocal, sourceRemote, targetLocal, targetRemote))
                                .onFailure(e -> log.error("source {} -- {} pipe to target {} -- {} failed",
                                        sourceLocal, sourceRemote, targetLocal, targetRemote, e));
                        targetSocket.pipeTo(sourceSocket)
                                .onSuccess(v -> log.debug("target {} -- {} pipe to source {} -- {} succeeded",
                                        targetLocal, targetRemote, sourceLocal, sourceRemote))
                                .onFailure(e -> log.error("target {} -- {} pipe to source {} -- {} failed",
                                        targetLocal, targetRemote, sourceLocal, sourceRemote, e));
                        log.debug("source {} -- {} bound to target {} -- {}",
                                sourceLocal, sourceRemote, targetLocal, targetRemote);
                        sourceSocket.resume();
                        targetSocket.resume();
                    });
        };
    }

    public static ReverseTcpProxy create(Vertx vertx,
                                         String targetHost, int targetPort, String name) {
        List<NetAddress> list = new ArrayList<>();
        TcpRoundRobinLoadBalancer lb = TcpRoundRobinLoadBalancer.create(list);
        return new ReverseTcpProxy(
                vertx.createNetServer(),
                vertx.createNetClient(),
                lb,
                list,
                name
        ).addNode(new NetAddress(targetHost, targetPort));
    }

    public static ReverseTcpProxy create(Vertx vertx,
                                         String targetHost, int targetPort) {
        List<NetAddress> list = new ArrayList<>();
        return new ReverseTcpProxy(
                vertx.createNetServer(),
                vertx.createNetClient(),
                TcpRoundRobinLoadBalancer.create(list),
                list,
                generateName()
        ).addNode(new NetAddress(targetHost, targetPort));
    }

    public static ReverseTcpProxy create(NetServer netServer, NetClient netClient, String targetHost, int targetPort) {
        List<NetAddress> list = new ArrayList<>();
        return new ReverseTcpProxy(
                netServer,
                netClient,
                TcpRoundRobinLoadBalancer.create(list),
                list,
                generateName()
        ).addNode(new NetAddress(targetHost, targetPort));
    }

    public static ReverseTcpProxy create(NetServer netServer, NetClient netClient, String targetHost, int targetPort, String name) {
        List<NetAddress> list = new ArrayList<>();
        return new ReverseTcpProxy(
                netServer,
                netClient,
                TcpRoundRobinLoadBalancer.create(list),
                list,
                name
        ).addNode(new NetAddress(targetHost, targetPort));
    }

    public static ReverseTcpProxy create(NetServer netServer, NetClient netClient,
                                         LoadBalancer<NetAddress> loadBalancer,
                                         List<NetAddress> netAddresses,
                                         String name) {
        return new ReverseTcpProxy(netServer, netClient, loadBalancer, netAddresses, name);
    }

    public ReverseTcpProxy port(int port) {
        this.sourcePort = port;
        return this;
    }

    public ReverseTcpProxy host(String host) {
        this.sourceHost = host;
        return this;
    }

    public ReverseTcpProxy addNode(NetAddress netAddress) {
        if (!netAddresses.contains(netAddress)) {
            netAddresses.add(netAddress);
        }
        return this;
    }


    public static String generateName() {
        final String prefix = ReverseTcpProxy.class.getSimpleName() + "-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger(ReverseTcpProxy.class.getName() + ".name", 0) + 1);
                System.setProperty(ReverseTcpProxy.class.getName() + ".name", next);
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
        if (netAddresses.size() <= 0) {
            throw new IllegalStateException("netAddresses size must be greater than 0");
        }
        netServer.connectHandler(connectHandler)
                .exceptionHandler(e -> log.error("{} socket errors happening before the connection is passed to the connectHandler", name, e))
                .listen(sourcePort, sourceHost)
                .onFailure(e -> log.error("{} start failed", name, e))
                .onSuccess(v -> log.info("{} started on {}:{}\nLB-Mode: {}\n  {}", name, sourceHost, sourcePort, lb.name(), netAddresses));
    }

    public void stop() {
        netServer.close()
                .onSuccess(v -> log.info("{} closed", name))
                .onFailure(e -> log.error("{} close failed", name, e));
    }

}

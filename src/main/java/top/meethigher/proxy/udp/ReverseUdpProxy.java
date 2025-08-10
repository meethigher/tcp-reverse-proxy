package top.meethigher.proxy.udp;

import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.net.SocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.LoadBalancer;
import top.meethigher.proxy.NetAddress;

import java.util.concurrent.ThreadLocalRandom;

/**
 * UDP反向代理
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @see <a href="https://github.com/meethigher/tcp-reverse-proxy/issues/24">支持UDP反代 · Issue #24 · meethigher/tcp-reverse-proxy</a>
 * @since 2025/08/10 19:15
 */
public class ReverseUdpProxy {
    private static final Logger log = LoggerFactory.getLogger(ReverseUdpProxy.class);

    protected static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    // 单位毫秒
    public static final long DST_TIMEOUT_DEFAULT = 60000;

    protected String sourceHost = "0.0.0.0";
    protected int sourcePort = 999;

    protected DatagramSocket src;

    protected final Vertx vertx;
    protected final long dstTimeout;
    protected final LoadBalancer<NetAddress> lb;
    protected final String name;

    protected ReverseUdpProxy(Vertx vertx, long dstTimeout,
                              LoadBalancer<NetAddress> loadBalancer,
                              String name) {
        this.vertx = vertx;
        this.dstTimeout = dstTimeout;
        this.lb = loadBalancer;
        this.name = name;
    }

    public ReverseUdpProxy host(String host) {
        this.sourceHost = host;
        return this;
    }

    public ReverseUdpProxy port(int port) {
        this.sourcePort = port;
        return this;
    }

    public void start() {
        src = vertx.createDatagramSocket();
        String srcLocal = sourceHost + ":" + sourcePort;
        src.handler(srcPk -> {
            NetAddress dstRemote = lb.next();
            SocketAddress srcRemote = srcPk.sender();
            log.debug("source {} -- {} connected. lb [{}] next target {}", srcRemote, srcLocal, lb.name(), dstRemote);
            DatagramSocket dst = vertx.createDatagramSocket();

            final long timerId = vertx.setTimer(dstTimeout, id -> dst.close());
            dst.handler(dstPk -> {
                SocketAddress sender = srcPk.sender();
                src.send(dstPk.data(), sender.port(), sender.host()).onComplete(ar -> {
                    if (ar.succeeded()) {
                        log.debug("target {} -- {} pipe to source {} -- {} succeeded",
                                dstRemote, dst.localAddress(), srcLocal, srcRemote);
                    } else {
                        log.error("target {} -- {} pipe to source {} -- {} failed",
                                dstRemote, dst.localAddress(), srcLocal, srcRemote, ar.cause());
                    }
                    vertx.cancelTimer(timerId);
                    dst.close();
                });
            });
            dst.send(srcPk.data(), dstRemote.getPort(), dstRemote.getHost())
                    .onFailure(e -> log.error("source {} -- {} pipe to target {} -- {} failed",
                            srcRemote, srcLocal, dst.localAddress(), dstRemote, e))
                    .onSuccess(v -> log.debug("source {} -- {} pipe to target {} -- {} succeeded",
                            srcRemote, srcLocal, dst.localAddress(), dstRemote));


        });
        src.listen(this.sourcePort, this.sourceHost)
                .onFailure(e -> log.error("{} start failed", name, e))
                .onSuccess(v -> log.info("{} started on {}:{}\nLB-Mode: {}\n {}",
                        name, sourceHost, sourcePort, lb.name(), lb.all()));
    }

    public void stop() {
        if (src != null) {
            src.close();
            log.info("{} closed", name);
        }
    }


    public static ReverseUdpProxy create(Vertx vertx, long dstTimeout,
                                         LoadBalancer<NetAddress> loadBalancer,
                                         String name) {
        return new ReverseUdpProxy(vertx, dstTimeout, loadBalancer, name);
    }

    public static ReverseUdpProxy create(Vertx vertx, LoadBalancer<NetAddress> loadBalancer) {
        return create(vertx, DST_TIMEOUT_DEFAULT, loadBalancer, generateName());
    }

    public static ReverseUdpProxy create(Vertx vertx, long dstTimeout, LoadBalancer<NetAddress> loadBalancer) {
        return create(vertx, dstTimeout, loadBalancer, generateName());
    }


    public static String generateName() {
        final String prefix = ReverseUdpProxy.class.getSimpleName() + "-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger(ReverseUdpProxy.class.getName() + ".name", 0) + 1);
                System.setProperty(ReverseUdpProxy.class.getName() + ".name", next);
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

}

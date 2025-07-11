package top.meethigher.proxy.tcp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    protected final String targetHost;
    protected final int targetPort;
    protected final String name;

    protected ReverseTcpProxy(NetServer netServer, NetClient netClient,
                              String targetHost, int targetPort, String name) {
        this.name = name;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.netServer = netServer;
        this.netClient = netClient;
        this.connectHandler = sourceSocket -> {
            // 暂停流读取
            sourceSocket.pause();
            SocketAddress sourceRemote = sourceSocket.remoteAddress();
            SocketAddress sourceLocal = sourceSocket.localAddress();
            log.debug("source {} -- {} connected", sourceLocal, sourceRemote);
            sourceSocket.closeHandler(v -> log.debug("source {} -- {} closed", sourceLocal, sourceRemote));
            netClient.connect(targetPort, targetHost)
                    .onFailure(e -> {
                        log.error("failed to connect to {}:{}", targetHost, targetPort, e);
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
                        targetSocket.closeHandler(v -> log.debug("target {} -- {} closed", targetLocal, targetRemote));

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
        return new ReverseTcpProxy(vertx.createNetServer(), vertx.createNetClient(), targetHost, targetPort, name);
    }

    public static ReverseTcpProxy create(Vertx vertx,
                                         String targetHost, int targetPort) {
        return new ReverseTcpProxy(vertx.createNetServer(), vertx.createNetClient(), targetHost, targetPort, generateName());
    }

    public static ReverseTcpProxy create(NetServer netServer, NetClient netClient, String targetHost, int targetPort) {
        return new ReverseTcpProxy(netServer, netClient, targetHost, targetPort, generateName());
    }

    public static ReverseTcpProxy create(NetServer netServer, NetClient netClient, String targetHost, int targetPort, String name) {
        return new ReverseTcpProxy(netServer, netClient, targetHost, targetPort, name);
    }

    public ReverseTcpProxy port(int port) {
        this.sourcePort = port;
        return this;
    }

    public ReverseTcpProxy host(String host) {
        this.sourceHost = host;
        return this;
    }


    protected static String generateName() {
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
        netServer.connectHandler(connectHandler).exceptionHandler(e -> log.error("connect failed", e));
        Future<NetServer> listenFuture = netServer.listen(sourcePort, sourceHost);

        Handler<AsyncResult<NetServer>> asyncResultHandler = ar -> {
            if (ar.succeeded()) {
                log.info("{} started on {}:{}", name, sourceHost, sourcePort);
            } else {
                Throwable e = ar.cause();
                log.error("{} start failed", name, e);
            }
        };
        listenFuture.onComplete(asyncResultHandler);
    }

    public void stop() {
        netServer.close()
                .onSuccess(v -> log.info("{} closed", name))
                .onFailure(e -> log.error("{} close failed", name, e));
    }

}

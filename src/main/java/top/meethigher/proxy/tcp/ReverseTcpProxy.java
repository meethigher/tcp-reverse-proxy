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

    private static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private String sourceHost = "0.0.0.0";

    private int sourcePort = 999;

    private final Handler<NetSocket> connectHandler;
    private final NetServer netServer;
    private final NetClient netClient;
    private final String targetHost;
    private final int targetPort;
    private final String name;

    private ReverseTcpProxy(NetServer netServer, NetClient netClient,
                            String targetHost, int targetPort, String name) {
        this.name = name;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.netServer = netServer;
        this.netClient = netClient;
        this.connectHandler = sourceSocket -> {
            // 暂停流读取
            sourceSocket.pause();
            netClient.connect(targetPort, targetHost)
                    .onFailure(e -> log.error("failed to connect to {}:{}", targetHost, targetPort, e))
                    .onSuccess(targetSocket -> {
                        SocketAddress sourceRemoteAddress = sourceSocket.remoteAddress();
                        SocketAddress sourceLocalAddress = sourceSocket.localAddress();
                        SocketAddress targetRemoteAddress = targetSocket.remoteAddress();
                        SocketAddress targetLocalAddress = targetSocket.localAddress();
                        log.debug("connected {} -- {} ({} -- {})", sourceRemoteAddress.toString(), sourceLocalAddress.toString(),
                                targetLocalAddress.toString(), targetRemoteAddress.toString());

                        // 暂停流读取
                        targetSocket.pause();


                        sourceSocket.closeHandler(v -> targetSocket.close()).pipeTo(targetSocket, ar -> {
                            if (ar.succeeded()) {
                                log.debug("pipeTo successful. {} --> {} --> {} --> {}",
                                        sourceRemoteAddress,
                                        sourceLocalAddress,
                                        targetLocalAddress,
                                        targetRemoteAddress);
                            } else {
                                log.error("pipeTo failed. {} --> {} --> {} --> {}",
                                        sourceRemoteAddress,
                                        sourceLocalAddress,
                                        targetLocalAddress,
                                        targetRemoteAddress,
                                        ar.cause());
                            }
                        });
                        targetSocket.closeHandler(v -> {
                            sourceSocket.close();
                            log.debug("closed {} -- {} ({} -- {})", sourceRemoteAddress.toString(), sourceLocalAddress.toString(),
                                    targetLocalAddress.toString(), targetRemoteAddress.toString());
                        }).pipeTo(sourceSocket, ar -> {
                            if (ar.succeeded()) {
                                log.debug("pipeTo successful. {} <-- {} <-- {} <-- {}",
                                        sourceRemoteAddress,
                                        sourceLocalAddress,
                                        targetLocalAddress,
                                        targetRemoteAddress);
                            } else {
                                log.error("pipeTo failed. {} <-- {} <-- {} <-- {}",
                                        sourceRemoteAddress,
                                        sourceLocalAddress,
                                        targetLocalAddress,
                                        targetRemoteAddress,
                                        ar.cause());
                            }
                        });

                        // 恢复流读取
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


    private static String generateName() {
        final String prefix = "ReverseTcpProxy-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger("top.meethigher.proxy.tcp.ReverseTcpProxy.name", 0) + 1);
                System.setProperty("top.meethigher.proxy.tcp.ReverseTcpProxy.name", next);
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

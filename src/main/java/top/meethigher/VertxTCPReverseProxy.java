package top.meethigher;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;


public class VertxTCPReverseProxy {

    private static final Logger log = LoggerFactory.getLogger(VertxTCPReverseProxy.class);

    private static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private String sourceHost = "0.0.0.0";

    private int sourcePort = 999;

    private final NetServer netServer;

    private final NetClient netClient;

    private final String targetHost;

    private final int targetPort;

    private final String name;

    private VertxTCPReverseProxy(NetServer netServer, NetClient netClient,
                                 String targetHost, int targetPort, String name) {
        this.name = name;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.netServer = netServer;
        this.netClient = netClient;
    }

    public static VertxTCPReverseProxy create(Vertx vertx,
                                              String targetHost, int targetPort, String name) {
        return new VertxTCPReverseProxy(vertx.createNetServer(), vertx.createNetClient(), targetHost, targetPort, name);
    }

    public static VertxTCPReverseProxy create(Vertx vertx,
                                              String targetHost, int targetPort) {
        return new VertxTCPReverseProxy(vertx.createNetServer(), vertx.createNetClient(), targetHost, targetPort, generateName());
    }

    public VertxTCPReverseProxy port(int port) {
        this.sourcePort = port;
        return this;
    }

    public VertxTCPReverseProxy host(String host) {
        this.sourceHost = host;
        return this;
    }


    private static String generateName() {
        final String prefix = "VertxTCPReverseProxy-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger("top.meethigher.VertxTCPReverseProxy.name", 0) + 1);
                System.setProperty("top.meethigher.VertxTCPReverseProxy.name", next);
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
        Handler<NetSocket> connectHandler = sourceSocket -> {
            sourceSocket.pause();
            netClient.connect(targetPort, targetHost)
                    .onSuccess(targetSocket -> {
                        log.info("connected {} <--> {}", sourceSocket.remoteAddress().toString(), targetSocket.remoteAddress().toString());
                        targetSocket.pause();
                        sourceSocket.pipeTo(targetSocket);
                        targetSocket.closeHandler(v -> {
                            sourceSocket.close().onSuccess(vv -> {
                                log.info("closed {} <--> {}", sourceSocket.remoteAddress().toString(), targetSocket.remoteAddress().toString());
                            });
                        }).pipeTo(sourceSocket);
                        sourceSocket.resume();
                        targetSocket.resume();
                    })
                    .onFailure(e -> log.error("failed to connect to {}:{}", targetHost, targetPort, e));

        };
        Handler<Throwable> connectFailedHandler = e -> log.error("connect failed", e);
        Handler<AsyncResult<NetServer>> asyncResultHandler = ar -> {
            if (ar.succeeded()) {
                log.info("{} started on {}:{}", name, sourceHost, sourcePort);
            } else {
                Throwable e = ar.cause();
                log.error("{} start failed", name, e);
            }
        };
        netServer.connectHandler(connectHandler).exceptionHandler(connectFailedHandler);
        Future<NetServer> listen = sourceHost == null ? netServer.listen(sourcePort) : netServer.listen(sourcePort, sourceHost);
        listen.onComplete(asyncResultHandler);
    }

    public void stop() {
        netServer.close()
                .onSuccess(v -> log.info("{} closed", name))
                .onFailure(e -> log.error("{} close failed", name, e));
    }

}

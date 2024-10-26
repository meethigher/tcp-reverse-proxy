package top.meethigher;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public class VertxHTTPReverseProxy {

    private static final Logger log = LoggerFactory.getLogger(VertxHTTPReverseProxy.class);

    private static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private String sourceHost = "0.0.0.0";

    private int sourcePort = 998;


    private final Router router;
    private final HttpServer httpServer;
    private final HttpClient httpClient;
    private final HttpClient httpsClient;
    private final String name;

    private VertxHTTPReverseProxy(Router router, HttpServer httpServer, HttpClient httpClient, HttpClient httpsClient, String name) {
        this.router = router;
        this.httpServer = httpServer;
        this.httpClient = httpClient;
        this.httpsClient = httpsClient;
        this.name = name;
    }

    public static VertxHTTPReverseProxy create(Vertx vertx, String name) {
        return new VertxHTTPReverseProxy(Router.router(vertx), vertx.createHttpServer(), vertx.createHttpClient(), vertx.createHttpClient(new HttpClientOptions().setSsl(true).setTrustAll(true)), name);
    }

    public static VertxHTTPReverseProxy create(Vertx vertx) {
        return new VertxHTTPReverseProxy(Router.router(vertx), vertx.createHttpServer(), vertx.createHttpClient(), vertx.createHttpClient(new HttpClientOptions().setSsl(true).setTrustAll(true)), generateName());
    }


    private static String generateName() {
        final String prefix = "VertxHTTPReverseProxy-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger("top.meethigher.VertxHTTPReverseProxy.name", 0) + 1);
                System.setProperty("top.meethigher.VertxHTTPReverseProxy.name", next);
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
        httpServer.requestHandler(router).exceptionHandler(e -> log.error("request failed", e));
        Future<HttpServer> listenFuture = httpServer.listen(sourcePort, sourceHost);

        Handler<AsyncResult<HttpServer>> asyncResultHandler = ar -> {
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
        httpServer.close()
                .onSuccess(v -> log.info("{} closed", name))
                .onFailure(e -> log.error("{} close failed", name, e));
    }
}

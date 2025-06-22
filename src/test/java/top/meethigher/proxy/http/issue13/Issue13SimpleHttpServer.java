package top.meethigher.proxy.http.issue13;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.locks.LockSupport;

public class Issue13SimpleHttpServer {
    private static final Logger log = LoggerFactory.getLogger(Issue13SimpleHttpServer.class);

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();


        HttpServerOptions httpServerOptions = new HttpServerOptions()
                // 服务端支持与客户端进行协商，支持通过alpn用于协商客户端和服务端使用http1.1还是http2
                // 开启h2c，使其支持http2，默认情况下http2只在开启了tls使用。如果不开启tls还想使用http2，那么需要开启h2c
                // alpn基于tls，若未开启tls，则不支持alpn
                .setAlpnVersions(Collections.unmodifiableList(Arrays.asList(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2)))
                .setUseAlpn(true)
                .setHttp2ClearTextEnabled(true);

        HttpServer httpServer = vertx.createHttpServer();
        httpServer.requestHandler(req -> {
            req.pause();
            long id = vertx.setTimer(5000, t -> {
                req.response().setStatusCode(400).end("body not exist");
            });
            req.response().setChunked(true);
            req.response().write("\nrequest headers:\n");
            for (String key : req.headers().names()) {
                req.response().write(key + ":" + req.headers().get(key) + "\n");
            }
            req.response().write("\nrequest body:\n");
            req.bodyHandler(buf -> {
                vertx.cancelTimer(id);
                log.info("received:\n{}", buf.toString());
                req.response().end(buf);
            });
            req.resume();
        });
        httpServer.listen(18080).onFailure(e -> {
            System.exit(1);
        });

        LockSupport.park();
    }
}

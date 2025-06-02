package top.meethigher.proxy.tcp.tunnel.issue9;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Issue9SimpleHttpServer {

    private static final Logger log = LoggerFactory.getLogger(Issue9SimpleHttpServer.class);
    private static final Vertx vertx = Vertx.vertx();

    public static void main(String[] args) {
        final String result = "<!DOCTYPE html><html><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\"><title>novnc2</title><link href=./static/css/app.c90a3de1d5a865cd4149616f9b8040a5.css rel=stylesheet></head><body><div id=app></div><script type=text/javascript src=./static/js/manifest.3ad1d5771e9b13dbdad2.js></script><script type=text/javascript src=./static/js/vendor.eba9acff8b0b3b1b22c4.js></script><script type=text/javascript src=./static/js/app.fef011cae8f5fbff4b55.js></script></body></html>";
        HttpServer httpServer = vertx.createHttpServer();
        httpServer.requestHandler(req -> {
            String address = req.connection().remoteAddress().toString();
            log.info("{} connected", address);
            req.connection().closeHandler(v -> {
                log.info("{} closed", address);
            });
            HttpServerResponse response = req.response();
            response.putHeader("Server", "nginx/1.18.0 (Ubuntu)");
            response.putHeader("Date", "Mon, 02 Jun 2025 07:22:44 GMT");
            response.putHeader("Content-Type", "text/html");
            response.putHeader("Content-Length", "512");
            response.putHeader("Last-Modified", "Sun, 04 Apr 2021 03:44:30 GMT");
            response.putHeader("ETag", "\"6069361e-200\"");
            response.putHeader("Accept-Ranges", "bytes");
            response.setStatusCode(200).end(result);
        }).listen(80).onFailure(e -> {
            log.error("http server start failed", e);
            System.exit(1);
        });
    }
}
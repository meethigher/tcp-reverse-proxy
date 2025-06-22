package top.meethigher.proxy.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.ext.web.Router;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.locks.LockSupport;

public class Issue13Test {

    private static final Vertx vertx = Vertx.vertx();
    private static final Logger log = LoggerFactory.getLogger(Issue13Test.class);

    @Test
    public void name() {

        HttpServerOptions httpServerOptions = new HttpServerOptions()
                // 服务端支持与客户端进行协商，支持通过alpn用于协商客户端和服务端使用http1.1还是http2
                // 开启h2c，使其支持http2，默认情况下http2只在开启了tls使用。如果不开启tls还想使用http2，那么需要开启h2c
                // alpn基于tls，若未开启tls，则不支持alpn
                .setAlpnVersions(Collections.unmodifiableList(Arrays.asList(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2)))
                .setUseAlpn(true)
                .setHttp2ClearTextEnabled(true);
        HttpServer server = vertx.createHttpServer(httpServerOptions);

        HttpClientOptions httpClientOptions = new HttpClientOptions()
                // 设置客户端默认使用的HTTP协议版本是http1.1，并且开启alpn支持协商http1.1和http2
                // alpn基于tls，若对方没有开启tls，则不支持alpn
                .setProtocolVersion(HttpVersion.HTTP_1_1)
                .setUseAlpn(true)
                .setAlpnVersions(new ArrayList<HttpVersion>() {{
                    add(HttpVersion.HTTP_1_1);
                    add(HttpVersion.HTTP_2);
                }});
        HttpClient client = vertx.createHttpClient(httpClientOptions);

        ReverseHttpProxy.create(Router.router(vertx), server, client)
                .port(18088)
                .addRoute(new ProxyRoute()
                        .setName("proxy")
                        .setTargetUrl("http://127.0.0.1:18080")
                        .setSourceUrl("/*"))
                .start();


        RequestOptions requestOptions = new RequestOptions()
                .setAbsoluteURI("http://127.0.0.1:18088/test")
                .setMethod(HttpMethod.POST);

        String body = "halo wode";

        // 发送http1.1的content-length请求体
        vertx.setTimer(4000, id -> {
            HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1));
            httpClient.request(requestOptions).onSuccess(req -> {
                req.send(body).onSuccess(resp -> {
                    resp.bodyHandler(buf -> {
                        log.info("{} content-length received:\n{}", resp.version(), buf.toString());
                    });
                });
            });
        });
        // 发送http1.1的transfer-encoding:chunked请求体
        vertx.setTimer(5000, id -> {
            HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_1));
            httpClient.request(requestOptions).onSuccess(req -> {
                req.setChunked(true);
                req.send(body).onSuccess(resp -> {
                    resp.bodyHandler(buf -> {
                        log.info("{} transfer-encoding received:\n{}", resp.version(), buf.toString());
                    });
                });
            });
        });

        // 发送http2的content-length请求体
        vertx.setTimer(6000, id -> {
            HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
            httpClient.request(requestOptions).onSuccess(req -> {
                req.send(body).onSuccess(resp -> {
                    resp.bodyHandler(buf -> {
                        log.info("{} content-length received:\n{}", resp.version(), buf.toString());
                    });
                });
            });
        });

        //发送http2的transfer-encoding:chunked请求体
        vertx.setTimer(7000, id -> {
            HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
            httpClient.request(requestOptions).onSuccess(req -> {
                req.setChunked(true);
                req.send(body).onSuccess(resp -> {
                    resp.bodyHandler(buf -> {
                        log.info("{} transfer-encoding received:\n{}", resp.version(), buf.toString());
                    });
                });
            });
        });

        LockSupport.park();
    }
}

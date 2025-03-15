package top.meethigher.proxy.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.ext.web.Router;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class ReverseHttpProxyBugTest {

    private static final Logger log = LoggerFactory.getLogger(ReverseHttpProxyBugTest.class);

    private static final Vertx vertx = Vertx.vertx();

    /**
     * 前置环境。
     * 启动一个10秒响应的backend
     * 启动一个反代backend的proxyserver
     */
    @Before
    public void server() {

        int backendServerPort = 888;
        int proxyServerPort = 8080;

        // backend-server
        vertx.createHttpServer().requestHandler(req -> {
            vertx.setTimer(Duration.ofSeconds(1).toMillis(), id -> {
                req.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "text/plain;charset=utf-8")
                        .end(req.uri());
            });
        }).listen(backendServerPort, ar -> {
            if (ar.succeeded()) {
                log.info("backend started on port {}", backendServerPort);
            } else {
                log.error("backend start error", ar.cause());
            }
        });

        // proxy-server
        Router router = Router.router(vertx);
        HttpServer httpServer = vertx.createHttpServer();
        HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions(), new PoolOptions());
        ReverseHttpProxy.create(router, httpServer, httpClient).port(proxyServerPort).addRoute(
                new ProxyRoute()
                        .setName("proxy")
                        .setSourceUrl("/*")
                        .setTargetUrl("http://127.0.0.1:" + backendServerPort)
        ).start();

    }


    /**
     * 客户端主动发起请求
     * 客户端--->proxy--->backend
     * <p>
     * 此时，客户端主动关闭，当proxy将backend的响应写回至客户端时，会发现连接已经断开了。
     * 客户端<-x-proxy<--backend
     * <p>
     * 此时就会报错: Response has already been written
     */
    @Test
    public void reqClose() throws Exception {

        HttpClient httpClient = vertx.createHttpClient();
        httpClient.request(new RequestOptions().setMethod(HttpMethod.GET).setAbsoluteURI("http://127.0.0.1:8080/bug-test"), ar -> {
            if (ar.succeeded()) {
                HttpClientRequest req = ar.result();

                // 模拟浏览器断开
                vertx.setTimer(Duration.ofSeconds(2).toMillis(), id -> {
                    req.connection().close();
                });

                req.send().onFailure(e -> {
                    log.error("send error", e);
                }).onSuccess(resp -> {
                    log.info("statusCode: {}", resp.statusCode());
                });
            } else {
                Throwable cause = ar.cause();
                log.error("request error", cause, cause);
            }
        });

        TimeUnit.SECONDS.sleep(20);
    }


    /**
     * 跨域第一种情况
     * 1. 响应头不允许跨域
     */
    @Test
    public void testAllowCros() throws Exception {
        // 转发一个不允许跨域的后端 直接以https://meethigher.top
        ReverseHttpProxy.create(vertx)
                .port(4321)
                .addRoute(new ProxyRoute()
                        .setSourceUrl("/*")
                        .setTargetUrl("https://meethigher.top")
                        .setFollowRedirects(false)
                        .setCorsControl(new ProxyRoute.CorsControl().setEnable(true).setAllowCors(true)))
                .start();
        LockSupport.park();
    }

    /**
     * 跨域第二种情况
     * 1. 响应头不允许跨域
     * 2. 后端拦截OPTIONS
     */
    @Test
    public void testAllowCros2() throws Exception {
        // 后端
        Vertx.vertx().createHttpServer().requestHandler(serverReq -> {
            if (serverReq.method().name().equalsIgnoreCase("options")) {
                serverReq.response().setStatusCode(403).end();
            } else {
                serverReq.response().setStatusCode(200).end();
            }
        }).listen(889);

        ReverseHttpProxy.create(vertx)
                .port(4321)
                .addRoute(new ProxyRoute()
                                .setSourceUrl("/*")
                                .setTargetUrl("http://127.0.0.1:889")
                                .setFollowRedirects(false)
                        .setCorsControl(new ProxyRoute.CorsControl().setEnable(true).setAllowCors(true))
                )
                .start();
        LockSupport.park();
    }
}

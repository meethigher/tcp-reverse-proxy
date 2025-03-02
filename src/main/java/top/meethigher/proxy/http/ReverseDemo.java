package top.meethigher.proxy.http;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class ReverseDemo {

    private static final Logger log = LoggerFactory.getLogger(ReverseDemo.class);

    /**
     * 不应该被复制的逐跳标头
     */
    protected final String[] hopByHopHeaders = new String[]{
            "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
            "TE", "Trailers", "Transfer-Encoding", "Upgrade"};


    protected boolean isHopByHopHeader(String headerName) {
        for (String hopByHopHeader : hopByHopHeaders) {
            if (hopByHopHeader.equalsIgnoreCase(headerName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 复制请求头。复制的过程中忽略逐跳标头
     */
    protected void copyRequestHeaders(HttpServerRequest realReq, HttpClientRequest proxyReq) {
        MultiMap realHeaders = realReq.headers();
        MultiMap proxyHeaders = proxyReq.headers();
        proxyHeaders.clear();
        for (String headerName : realHeaders.names()) {
            // 若是逐跳标头，则跳过
            if (isHopByHopHeader(headerName)) {
                continue;
            }
            // 针对Host请求头进行忽略
            if ("host".equalsIgnoreCase(headerName)) {
                continue;
            }
            proxyHeaders.set(headerName, realHeaders.get(headerName));
        }
    }

    /**
     * 复制响应头。复制的过程中忽略逐跳标头
     */
    protected void copyResponseHeaders(HttpServerResponse realResp, HttpClientResponse proxyResp) {
        MultiMap proxyHeaders = proxyResp.headers();
        MultiMap realHeaders = realResp.headers();
        realHeaders.clear();
        for (String headerName : proxyHeaders.names()) {
            // 若是逐跳标头，则跳过
            if (isHopByHopHeader(headerName)) {
                continue;
            }
            realHeaders.set(headerName, proxyHeaders.get(headerName));
        }
    }

    /**
     * 发起请求Handler
     */
    protected Handler<AsyncResult<HttpClientResponse>> sendRequestHandler(HttpServerRequest realReq, HttpServerResponse realResp, String realUrl) {
        return ar -> {
            if (ar.succeeded()) {
                HttpClientResponse proxyResp = ar.result();
                // 复制响应头。复制的过程中忽略逐跳标头
                copyResponseHeaders(realResp, proxyResp);
                if (!realResp.headers().contains("Content-Length")) {
                    realResp.setChunked(true);
                }
                proxyResp.pipeTo(realResp);
                log.info("{} {} {}", realReq.method().name(), realUrl, realResp.getStatusCode());
            } else {
                Throwable e = ar.cause();
                log.error("{} {} send request error", realReq.method().name(), realUrl, e);
            }
        };
    }

    /**
     * 建立连接Handler
     */
    protected Handler<AsyncResult<HttpClientRequest>> connectHandler(HttpServerRequest realReq, HttpServerResponse realResp, String realUrl) {
        return ar -> {
            if (ar.succeeded()) {
                HttpClientRequest proxyReq = ar.result();
                // 复制请求头。复制的过程中忽略逐跳标头
                copyRequestHeaders(realReq, proxyReq);
                // 若存在请求体，则将请求体复制。使用流式复制，避免占用大量内存
                if (proxyReq.headers().contains("Content-Length") || proxyReq.headers().contains("Transfer-Encoding")) {
                    realReq.pipeTo(proxyReq);
                }
                // 发送请求
                proxyReq.send().onComplete(sendRequestHandler(realReq, realResp, realUrl));
            } else {
                Throwable e = ar.cause();
                log.error("{} {} open connection error", realReq.method().name(), realUrl, e);
            }

        };
    }

    public void test(HttpServer httpServer, HttpClient httpClient, Router router) {
        String source = "/test/*";
        String target = "https://reqres.in";
        UrlParser.ParsedUrl targetUrl = UrlParser.parseUrl(target);
        router.route(source).handler(routingContextHandler(httpClient, targetUrl));
    }

    private Handler<RoutingContext> routingContextHandler(HttpClient httpClient, UrlParser.ParsedUrl targetUrl) {
        return ctx -> {
            HttpServerRequest realReq = ctx.request();
            HttpServerResponse realResp = ctx.response();
            Route route = ctx.currentRoute();
            String path = route.getPath();
            String routePathTrim = path.replaceAll("^/+", "");
            // vertx的uri()是包含query参数的。而path()才是我们常说的不带有query的uri
            String realUrl = UrlParser
                    .getUrl(new UrlParser.ParsedUrl(
                            targetUrl.isSsl,
                            targetUrl.host,
                            targetUrl.port,
                            targetUrl.uri + realReq.path().replace(routePathTrim, ""),
                            realReq.query()
                    ));

            // 构建请求参数
            RequestOptions requestOptions = new RequestOptions();
            requestOptions.setAbsoluteURI(realUrl);
            requestOptions.setMethod(realReq.method());

            // 请求
            httpClient.request(requestOptions).onComplete(connectHandler(realReq, realResp, realUrl));
        };
    }

    public static void main(String[] args) {
        ReverseDemo reverseDemo = new ReverseDemo();

        Vertx vertx = Vertx.vertx();

        HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions.setHttp2ClearTextEnabled(true).setAlpnVersions(Arrays.asList(HttpVersion.HTTP_2));

        HttpServer httpServer = vertx.createHttpServer(httpServerOptions);
        HttpClient httpClient = vertx.createHttpClient();

        Router router = Router.router(vertx);
        reverseDemo.test(httpServer, httpClient, router);

        httpServer.requestHandler(router).listen(8080).onFailure(e -> {
            log.error("server start failed", e);
        });
    }

}

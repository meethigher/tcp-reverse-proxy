package top.meethigher.proxy.http;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于Vertx实现的HTTP反向代理
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2024/10/30 23:05
 */
public class VertxHTTPReverseProxy {

    /**
     * ProxyRoute名称
     */
    public static final String P_NAME = "name";

    /**
     * 当前url
     */
    public static final String P_SOURCE_URL = "sourceUrl";

    /**
     * 目标url
     */
    public static final String P_TARGET_URL = "targetUrl";

    /**
     * 是否保留真实的客户端ip。通过http请求头X-Forwarded-For进行设置客户端ip
     */
    public static final String P_FORWARD_IP = "forwardIp";

    /**
     * 是否保留真实的host地址
     */
    public static final String P_PRESERVE_HOST = "preserveHost";

    /**
     * 是否保留真实的cookie信息
     */
    public static final String P_PRESERVE_COOKIES = "preserveCookies";

    /**
     * 是否跟随跳转
     */
    public static final String P_FOLLOW_REDIRECTS = "followRedirects";

    /**
     * 是否启用日志
     */
    public static final String P_LOG = "log.enable";

    /**
     * 日志格式。当启用日志后，该参数方可生效
     */
    public static final String P_LOG_FORMAT = "log.logFormat";

    /**
     * 是否开启跨域控制。若开启，则由代理服务接管跨域
     */
    public static final String P_CORS_CONTROL = "corsControl.enable";

    /**
     * 是否允许跨域。当启用跨域控制后，该参数方可生效
     */
    public static final String P_ALLOW_CORS = "corsControl.allowCors";


    private static final Logger log = LoggerFactory.getLogger(VertxHTTPReverseProxy.class);

    private static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private String sourceHost = "0.0.0.0";

    private int sourcePort = 998;


    private final Router router;
    private final Handler<RoutingContext> routingContextHandler;
    private final HttpServer httpServer;
    private final HttpClient httpClient;
    private final String name;
    private final String P_METADATA_CONFIG = "cfg";
    private final String P_STATE = "state";

    private VertxHTTPReverseProxy(Router router, HttpServer httpServer, HttpClient httpClient, String name) {
        this.router = router;
        this.httpServer = httpServer;
        this.httpClient = httpClient;
        this.name = name;
        this.routingContextHandler = ctx -> {
            Route route = ctx.currentRoute();
            ProxyRoute proxyRoute = getProxyRoute(route);
            String[] formatTargetUrl;
            try {
                formatTargetUrl = proxyRoute.formatTargetUrl();
            } catch (Exception e) {
                ctx.response().setStatusCode(400).end(e.getMessage());
                return;
            }
            HttpServerRequest request = ctx.request();
            String uri = request.uri();
            String path;
            if (route.getPath().endsWith("/")) {
                path = uri.substring(route.getPath().length() - 1);
            } else {
                path = uri.substring(route.getPath().length());
            }
            RequestOptions requestOptions = new RequestOptions()
                    .setSsl("https".equalsIgnoreCase(formatTargetUrl[0]))
                    .setHost(formatTargetUrl[1])
                    .setPort(Integer.valueOf(formatTargetUrl[2]))
                    .setURI(joinURI(formatTargetUrl[3], path));
            System.out.println(requestOptions.getURI());
            httpClient.request(requestOptions)
                    .onFailure(e -> {
                        ctx.response().setStatusCode(502).end(e.getMessage());
                    })
                    .onSuccess(r -> {
                        r.headers().setAll(request.headers());
                        r.putHeader("Host", "reqres.in");
                        r.send()
                                .onSuccess(r1 -> {
                                    ctx.response()
                                            .setStatusCode(r1.statusCode())
                                            .headers().setAll(r1.headers());
                                    r1.handler(data -> {
                                        ctx.response().write(data);
                                    });
                                    r1.endHandler(v -> ctx.response().end());

                                })
                                .onFailure(e1 -> {
                                    ctx.response().setStatusCode(500).end(e1.getMessage());
                                });
                    });
        };
    }

    private static String joinURI(String uri1, String uri2) {
        if (uri1.endsWith("/") && uri2.startsWith("/")) {
            // 两边都有 '/'
            return uri1 + uri2.substring(1);
        } else if (!uri1.endsWith("/") && !uri2.startsWith("/")) {
            // 两边都没有 '/'
            return uri1 + "/" + uri2;
        } else {
            // 只有一个有 '/'
            return uri1 + uri2;
        }
    }


    public static VertxHTTPReverseProxy create(Vertx vertx, String name) {
        return new VertxHTTPReverseProxy(Router.router(vertx), vertx.createHttpServer(), vertx.createHttpClient(), name);
    }

    public static VertxHTTPReverseProxy create(Vertx vertx) {
        return new VertxHTTPReverseProxy(Router.router(vertx), vertx.createHttpServer(), vertx.createHttpClient(), generateName());
    }

    public static VertxHTTPReverseProxy create(Router router, HttpServer httpServer, HttpClient httpClient, String name) {
        return new VertxHTTPReverseProxy(router, httpServer, httpClient, name);
    }


    public static VertxHTTPReverseProxy create(Router router, HttpServer httpServer, HttpClient httpClient) {
        return new VertxHTTPReverseProxy(router, httpServer, httpClient, generateName());
    }

    private static String generateName() {
        final String prefix = "VertxHTTPReverseProxy-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger("top.meethigher.proxy.http.VertxHTTPReverseProxy.name", 0) + 1);
                System.setProperty("top.meethigher.proxy.http.VertxHTTPReverseProxy.name", next);
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

    public boolean enabled(String name) {
        for (Route route : router.getRoutes()) {
            ProxyRoute proxyRoute = getProxyRoute(route);
            if (name.equals(proxyRoute.getName())) {
                return route.getMetadata(P_STATE);
            }
        }
        return false;
    }

    private ProxyRoute getProxyRoute(Route route) {
        return route.getMetadata(P_METADATA_CONFIG);
    }


    /**
     * 添加ProxyRoute
     */
    public void addRoute(ProxyRoute proxyRoute) {
        router.route(proxyRoute.getSourceUrl())
                .putMetadata(P_STATE, true)
                .putMetadata(P_METADATA_CONFIG, proxyRoute)
                .handler(routingContextHandler);
    }

    /**
     * 删除ProxyRout，并将删除后的ProxyRout返回
     */
    public ProxyRoute removeRoute(String name) {
        for (Route route : router.getRoutes()) {
            ProxyRoute proxyRoute = getProxyRoute(route);
            if (name.equals(proxyRoute.getName())) {
                route.remove();
                return proxyRoute;
            }
        }
        return null;
    }

    /**
     * 启用ProxyRout，并将启用后的ProxyRout返回
     */
    public ProxyRoute enableRoute(String name) {
        for (Route route : router.getRoutes()) {
            ProxyRoute proxyRoute = getProxyRoute(route);
            if (name.equals(proxyRoute.getName())) {
                route.enable();
                route.putMetadata(P_STATE, true);
                return proxyRoute;
            }
        }
        return null;
    }

    /**
     * 停用ProxyRout，并将停用后的ProxyRout返回
     */
    public ProxyRoute disableRoute(String name) {
        for (Route route : router.getRoutes()) {
            ProxyRoute proxyRoute = getProxyRoute(route);
            if (name.equals(proxyRoute.getName())) {
                route.disable();
                route.putMetadata(P_STATE, false);
                return proxyRoute;
            }
        }
        return null;
    }

    /**
     * 获取当前所有ProxyRout
     */
    public List<ProxyRoute> getRoutes() {
        List<ProxyRoute> proxyRoutes = new ArrayList<>();
        for (Route route : router.getRoutes()) {
            ProxyRoute proxyRoute = getProxyRoute(route);
            proxyRoute.setEnable(route.getMetadata(P_STATE));
            proxyRoutes.add(proxyRoute);
        }
        return proxyRoutes;
    }

    public VertxHTTPReverseProxy port(int port) {
        this.sourcePort = port;
        return this;
    }

    public VertxHTTPReverseProxy host(String host) {
        this.sourceHost = host;
        return this;
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

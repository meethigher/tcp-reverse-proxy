package top.meethigher.proxy.http;

import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于Vertx实现的HTTP反向代理
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2024/10/30 23:05
 */
public class ReverseHttpProxy {

    private static final Logger log = LoggerFactory.getLogger(ReverseHttpProxy.class);


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
     * 是否启用httpKeepAlive
     */
    public static final String P_HTTP_KEEPALIVE = "httpKeepAlive";

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


    /**
     * 请求发送的毫秒时间戳
     */
    public static final String P_SEND_TIMESTAMP = "send.timestamp";


    protected static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();


    private String sourceHost = "0.0.0.0";
    private int sourcePort = 998;


    private final HttpServer httpServer;
    private final HttpClient httpClient;
    private final Router router;
    private final String name;


    /**
     * 不应该被复制的逐跳标头
     */
    protected final String[] hopByHopHeaders = new String[]{
            "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
            "TE", "Trailers", "Transfer-Encoding", "Upgrade"};

    /**
     * 跨域相关的响应头
     */
    protected final List<String> allowCORSHeaders = Arrays.asList(
            "access-control-allow-origin",//指定哪些域可以访问资源。可以是特定域名，也可以是通配符 *，表示允许所有域访问。
            "access-control-allow-methods",//指定允许的HTTP方法，如 GET、POST、PUT、DELETE 等。
            "access-control-allow-headers",//指定允许的请求头。
            "access-control-allow-credentials",//指定是否允许发送凭据（如Cookies）。值为 true 表示允许，且不能使用通配符 *。
            "access-control-expose-headers",//指定哪些响应头可以被浏览器访问。
            "access-control-max-age",//指定预检请求的结果可以被缓存的时间（以秒为单位）。
            "access-control-request-method",//在预检请求中使用，指示实际请求将使用的方法。
            "access-control-request-headers"//在预检请求中使用，指示实际请求将使用的自定义头。
    );

    /**
     * 默认的日志格式
     */
    protected final String LOG_FORMAT_DEFAULT = "{name} -- {method} -- {userAgent} -- {remoteAddr}:{remotePort} -- {source} --> {target} -- {statusCode} consumed {consumedMills} ms";

    public ReverseHttpProxy(HttpServer httpServer, HttpClient httpClient, Router router, String name) {
        this.httpServer = httpServer;
        this.httpClient = httpClient;
        this.router = router;
        this.name = name;
    }

    public static ReverseHttpProxy create(Vertx vertx, String name) {
        return new ReverseHttpProxy(vertx.createHttpServer(), vertx.createHttpClient(), Router.router(vertx), name);
    }

    public static ReverseHttpProxy create(Vertx vertx) {
        return new ReverseHttpProxy(vertx.createHttpServer(), vertx.createHttpClient(), Router.router(vertx), generateName());
    }

    public static ReverseHttpProxy create(Router router, HttpServer httpServer, HttpClient httpClient, String name) {
        return new ReverseHttpProxy(httpServer, httpClient, router, name);
    }


    public static ReverseHttpProxy create(Router router, HttpServer httpServer, HttpClient httpClient) {
        return new ReverseHttpProxy(httpServer, httpClient, router, generateName());
    }

    protected static String generateName() {
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

    public ReverseHttpProxy port(int port) {
        this.sourcePort = port;
        return this;
    }

    public ReverseHttpProxy host(String host) {
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

    public ReverseHttpProxy addRoute(ProxyRoute proxyRoute) {
        return addRoute(proxyRoute, null);
    }


    /**
     * order越小，优先级越高
     */
    public ReverseHttpProxy addRoute(
            ProxyRoute proxyRoute,
            Integer order
    ) {
        Route route = router.route(proxyRoute.getSourceUrl()).setName(proxyRoute.getName());
        if (order != null) {
            route.order(order);
        }
        Map<String, String> map = proxyRoute.toMap();
        for (String key : map.keySet()) {
            route.putMetadata(key, map.get(key));
        }
        route.handler(routingContextHandler(httpClient));
        jsonLog(proxyRoute);
        return this;
    }

    private void jsonLog(ProxyRoute proxyRoute) {
        Map<String, Object> map = new LinkedHashMap<>(proxyRoute.toMap());
        log.info("add Route\n{}", new JsonObject(map).encodePrettily());
    }


    public ReverseHttpProxy removeRoute(String name) {
        for (Route route : getRoutes()) {
            if (name.equals(route.getName())) {
                route.remove();
                log.info("remove Route {}--{}", name, route.getMetadata(P_SOURCE_URL));
                //break;//允许名称重复的一并删除
            }
        }
        return this;
    }

    public List<Route> getRoutes() {
        return router.getRoutes();
    }


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
    protected void copyRequestHeaders(Route route, HttpServerRequest realReq, HttpClientRequest proxyReq) {
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

        // 传递真实客户端信息
        if (route.getMetadata(P_FORWARD_IP) != null && Boolean.parseBoolean(route.getMetadata(P_FORWARD_IP))) {
            String firstForward = realReq.getHeader("X-Forwarded-For");
            String secondForward = realReq.remoteAddress() == null ? null : realReq.remoteAddress().hostAddress();
            String forward = firstForward == null ? secondForward : firstForward + ", " + secondForward;
            proxyReq.putHeader("X-Forwarded-For", forward);
            proxyReq.putHeader("X-Forwarded-Proto", realReq.scheme());
        }
        // 传递代理主机Host
        if (route.getMetadata(P_PRESERVE_HOST) != null && Boolean.parseBoolean(route.getMetadata(P_PRESERVE_HOST))) {
            proxyReq.putHeader("Host", realReq.headers().get("Host"));
        }
        // 控制实际代理请求的长连接
        if (route.getMetadata(P_HTTP_KEEPALIVE) != null && Boolean.parseBoolean(route.getMetadata(P_HTTP_KEEPALIVE))) {
            proxyReq.putHeader("Connection", "keep-alive");
        } else {
            proxyReq.putHeader("Connection", "close");
        }
    }

    /**
     * 复制响应头。复制的过程中忽略逐跳标头
     */
    protected void copyResponseHeaders(Route route, HttpServerRequest realReq, HttpServerResponse realResp, HttpClientResponse proxyResp) {
        MultiMap proxyHeaders = proxyResp.headers();
        MultiMap realHeaders = realResp.headers();
        realHeaders.clear();

        Map<String, String> needSetHeaderMap = new LinkedHashMap<>();
        for (String headerName : proxyHeaders.names()) {
            // 若是逐跳标头，则跳过
            if (isHopByHopHeader(headerName)) {
                continue;
            }
            // 保留Cookie
            if ("Set-Cookie".equalsIgnoreCase(headerName) || "Set-Cookie2".equalsIgnoreCase(headerName)) {
                if (route.getMetadata(P_PRESERVE_COOKIES) != null && Boolean.parseBoolean(route.getMetadata(P_PRESERVE_COOKIES))) {
                    needSetHeaderMap.put(headerName, proxyHeaders.get(headerName));
                }
            }
            // 重写重定向Location
            else if ("Location".equalsIgnoreCase(headerName)) {
                String value = rewriteLocation(route, realReq.absoluteURI(), proxyHeaders.get(headerName));
                needSetHeaderMap.put(headerName, value);
            } else {
                needSetHeaderMap.put(headerName, proxyHeaders.get(headerName));
            }
        }
        // 跨域由代理掌控
        if (route.getMetadata(P_CORS_CONTROL) != null && Boolean.parseBoolean(route.getMetadata(P_CORS_CONTROL))) {
            // 允许跨域
            if (route.getMetadata(P_ALLOW_CORS) != null && Boolean.parseBoolean(route.getMetadata(P_ALLOW_CORS))) {
                String header = realReq.getHeader("origin");
                if (header == null || header.isEmpty()) {
                    needSetHeaderMap.put("Access-Control-Allow-Origin", "*");
                } else {
                    needSetHeaderMap.put("Access-Control-Allow-Origin", header);
                }
                needSetHeaderMap.put("Access-Control-Allow-Methods", "*");
                needSetHeaderMap.put("Access-Control-Allow-Headers", "*");
                needSetHeaderMap.put("Access-Control-Allow-Credentials", "true");
                needSetHeaderMap.put("Access-Control-Expose-Headers", "*");
            }
            // 不允许跨域
            else {
                Iterator<String> iterator = needSetHeaderMap.keySet().iterator();
                while (iterator.hasNext()) {
                    String next = iterator.next().toLowerCase(Locale.ROOT);
                    if (allowCORSHeaders.contains(next)) {
                        iterator.remove();
                    }
                }
            }
        }


        for (String key : needSetHeaderMap.keySet()) {
            realHeaders.set(key, needSetHeaderMap.get(key));
        }
    }

    /**
     * 重写Location
     */
    protected String rewriteLocation(Route route, String url, String location) {
        // 若重定向的地址，在反向代理的范围内，则进行重写
        String targetUrl = route.getMetadata(P_TARGET_URL).toString();
        if (location != null && location.startsWith(targetUrl)) {
            UrlParser.ParsedUrl parsedUrl = UrlParser.parseUrl(url);
            String locationUri = location.replace(targetUrl, "");
            return parsedUrl.getFormatHostPort() + (route.getMetadata(P_SOURCE_URL).toString().replace("/*", "")) + locationUri;
        }
        return location;
    }

    protected void doLog(Route route, HttpServerRequest realReq, HttpServerResponse realResp, String realUrl) {

        if (route.getMetadata(P_LOG) != null && Boolean.parseBoolean(route.getMetadata(P_LOG))) {
            String logFormat = route.getMetadata(P_LOG_FORMAT).toString();
            if (logFormat == null || logFormat.isEmpty()) {
                logFormat = LOG_FORMAT_DEFAULT;
            }
            String logInfo = logFormat
                    .replace("{name}", route.getName())
                    .replace("{method}", realReq.method().toString())
                    .replace("{userAgent}", realReq.getHeader("User-Agent"))
                    .replace("{remoteAddr}", realReq.remoteAddress().hostAddress())
                    .replace("{remotePort}", String.valueOf(realReq.remoteAddress().port()))
                    .replace("{source}", realReq.uri())
                    .replace("{target}", realUrl)
                    .replace("{statusCode}", String.valueOf(realResp.getStatusCode()))
                    .replace("{consumedMills}", String.valueOf(System.currentTimeMillis() - (Long) route.getMetadata(P_SEND_TIMESTAMP)));
            log.info(logInfo);
        }
    }

    /**
     * 发起请求Handler
     */
    protected Handler<AsyncResult<HttpClientResponse>> sendRequestHandler(Route route, HttpServerRequest realReq, HttpServerResponse realResp, String realUrl) {
        return ar -> {
            if (ar.succeeded()) {
                HttpClientResponse proxyResp = ar.result();
                // 复制响应头。复制的过程中忽略逐跳标头
                copyResponseHeaders(route, realReq, realResp, proxyResp);
                if (!realResp.headers().contains("Content-Length")) {
                    realResp.setChunked(true);
                }
                // 设置响应码
                realResp.setStatusCode(proxyResp.statusCode());
                // 流输出
                proxyResp.pipeTo(realResp);
                doLog(route, realReq, realResp, realUrl);
            } else {
                Throwable e = ar.cause();
                log.error("{} {} send request error", realReq.method().name(), realUrl, e);
            }
        };
    }

    /**
     * 建立连接Handler
     */
    protected Handler<AsyncResult<HttpClientRequest>> connectHandler(Route route, HttpServerRequest realReq, HttpServerResponse realResp, String realUrl) {
        return ar -> {
            if (ar.succeeded()) {
                HttpClientRequest proxyReq = ar.result();
                // 复制请求头。复制的过程中忽略逐跳标头
                copyRequestHeaders(route, realReq, proxyReq);
                // 若存在请求体，则将请求体复制。使用流式复制，避免占用大量内存
                if (proxyReq.headers().contains("Content-Length") || proxyReq.headers().contains("Transfer-Encoding")) {
                    realReq.pipeTo(proxyReq);
                }
                // 发送请求
                route.putMetadata(P_SEND_TIMESTAMP, System.currentTimeMillis());
                proxyReq.send().onComplete(sendRequestHandler(route, realReq, realResp, realUrl));
            } else {
                Throwable e = ar.cause();
                log.error("{} {} open connection error", realReq.method().name(), realUrl, e);
            }

        };
    }

    /**
     * 路由处理Handler
     */
    protected Handler<RoutingContext> routingContextHandler(HttpClient httpClient) {
        return ctx -> {
            // vertx的uri()是包含query参数的。而path()才是我们常说的不带有query的uri
            Route route = ctx.currentRoute();
            String result = route.getMetadata(P_TARGET_URL).toString();
            HttpServerRequest realReq = ctx.request();
            HttpServerResponse realResp = ctx.response();
            String absoluteURI = realReq.absoluteURI();
            UrlParser.ParsedUrl parsedUrl = UrlParser.parseUrl(absoluteURI);
            String prefix = parsedUrl.getFormatHostPort() + (route.getMetadata(P_SOURCE_URL).toString().replace("/*", ""));
            String realUrl = result + (parsedUrl.getFormatUrl().replace(prefix, ""));


            // 构建请求参数
            RequestOptions requestOptions = new RequestOptions();
            requestOptions.setAbsoluteURI(realUrl);
            requestOptions.setMethod(realReq.method());
            requestOptions.setFollowRedirects(route.getMetadata(P_FOLLOW_REDIRECTS) != null && Boolean.parseBoolean(route.getMetadata(P_FOLLOW_REDIRECTS)));

            // 请求
            httpClient.request(requestOptions).onComplete(connectHandler(route, realReq, realResp, realUrl));
        };
    }


}

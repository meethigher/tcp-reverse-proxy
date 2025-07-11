package top.meethigher.proxy.http;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static top.meethigher.proxy.http.UrlParser.fastReplace;

/**
 * 基于Vertx实现的HTTP反向代理
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2024/10/30 23:05
 */
public class ReverseHttpProxy {

    private static final Logger log = LoggerFactory.getLogger(ReverseHttpProxy.class);


    /**
     * 默认的日志格式
     */
    public static final String LOG_FORMAT_DEFAULT = "" +
            "{name} -- " +
            "{method} -- " +
            "{serverHttpVersion} -- " +
            "{clientHttpVersion} -- " +
            "{userAgent} -- " +
            "{serverRemoteAddr} -- " +
            "{serverLocalAddr} -- " +
            "{clientLocalAddr} -- " +
            "{clientRemoteAddr} -- " +
            "{sourceUri} -- " +
            "{proxyUrl} -- " +
            "{statusCode} -- " +
            "consumed {consumedMills} ms";

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
    protected static final String INTERNAL_SEND_TIMESTAMP = "INTERNAL_SEND_TIMESTAMP";

    /**
     * 连接状态：客户端--代理服务
     */
    protected static final String INTERNAL_SERVER_CONNECTION_OPEN = "INTERNAL_SERVER_CONNECTION_OPEN";

    /**
     * 连接状态：代理服务--后端服务
     */
    protected static final String INTERNAL_CLIENT_CONNECTION_OPEN = "INTERNAL_CLIENT_CONNECTION_OPEN";

    /**
     * 代理服务接收请求的HTTP版本
     */
    protected static final String INTERNAL_SERVER_HTTP_VERSION = "INTERNAL_SERVER_HTTP_VERSION";

    /**
     * 代理服务发起求的HTTP版本
     */
    protected static final String INTERNAL_CLIENT_HTTP_VERSION = "INTERNAL_CLIENT_HTTP_VERSION";

    /**
     * 代理请求Method
     */
    protected static final String INTERNAL_METHOD = "INTERNAL_METHOD";

    /**
     * 代理服务收到的UserAgent
     */
    protected static final String INTERNAL_USER_AGENT = "INTERNAL_USER_AGENT";


    /**
     * 代理服务收到的请求的远端地址
     */
    protected static final String INTERNAL_SERVER_REMOTE_ADDR = "INTERNAL_SERVER_REMOTE_ADDR";

    /**
     * 代理服务收到的请求的本地地址
     */
    protected static final String INTERNAL_SERVER_LOCAL_ADDR = "INTERNAL_SERVER_LOCAL_ADDR";

    /**
     * 代理服务发起请求的本端地址
     */
    protected static final String INTERNAL_CLIENT_LOCAL_ADDR = "INTERNAL_CLIENT_LOCAL_ADDR";

    /**
     * 代理服务发起请求的远端地址
     */
    protected static final String INTERNAL_CLIENT_REMOTE_ADDR = "INTERNAL_CLIENT_REMOTE_ADDR";


    /**
     * 代理服务收到的请求路径
     */
    protected static final String INTERNAL_SOURCE_URI = "INTERNAL_SOURCE_URI";

    /**
     * 代理服务请求的实际代理路径
     */
    protected static final String INTERNAL_PROXY_URL = "INTERNAL_PROXY_URL";

    /**
     * 代理服务响应码
     */
    protected static final String INTERNAL_STATUS_CODE = "INTERNAL_STATUS_CODE";

    /**
     * 静态资源前缀
     */
    protected static final String STATIC = "static:";

    protected static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();


    protected String sourceHost = "0.0.0.0";
    protected int sourcePort = 998;


    protected final HttpServer httpServer;
    protected final HttpClient httpClient;
    protected final Router router;
    protected final String name;


    /**
     * 不应该被复制的逐跳标头
     */
    protected static final Set<String> HOP_BY_HOP_HEADERS_SET = new HashSet<>(Arrays.asList(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade"));

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


    protected ReverseHttpProxy(HttpServer httpServer, HttpClient httpClient, Router router, String name) {
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
        final String prefix = ReverseHttpProxy.class.getSimpleName() + "-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger(ReverseHttpProxy.class.getName() + ".name", 0) + 1);
                System.setProperty(ReverseHttpProxy.class.getName() + ".name", next);
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

    /**
     * 更新路由内数据。
     * 该内容以路由为单位。
     *
     * @param route 路由对象
     * @param key   元数据键
     * @param value 元数据值
     */
    protected void setRouteMetadata(Route route, String key, Object value) {
        route.putMetadata(key, value == null ? "" : value);
    }

    protected Object getRouteMetadata(Route route, String key) {
        Object metadata = route.getMetadata(key);
        return metadata == null ? "" : metadata;
    }


    /**
     * 更新请求上下文内数据。
     * 该内容以请求为单位。对于请求来说，是线程安全的。
     *
     * @param ctx   路由上下文
     * @param key   数据键
     * @param value 数据值
     */
    protected void setContextData(RoutingContext ctx, String key, Object value) {
        ctx.put(key, value == null ? "null" : value);
    }

    protected Object getContextData(RoutingContext ctx, String key) {
        Object metadata = ctx.get(key);
        return metadata == null ? "null" : metadata;
    }

    protected HttpServerResponse setStatusCode(RoutingContext ctx, HttpServerResponse resp, int code) {
        resp.setStatusCode(code);
        setContextData(ctx, INTERNAL_STATUS_CODE, code);
        return resp;
    }

    protected void doLog(RoutingContext ctx) {
        if (getContextData(ctx, P_LOG) != null && Boolean.parseBoolean(getContextData(ctx, P_LOG).toString())) {
            String logFormat = getContextData(ctx, P_LOG_FORMAT).toString();
            if (logFormat == null || logFormat.isEmpty()) {
                logFormat = LOG_FORMAT_DEFAULT;
            }
            String logInfo = logFormat
                    .replace("{name}", getContextData(ctx, P_NAME).toString())
                    .replace("{method}", getContextData(ctx, INTERNAL_METHOD).toString())
                    .replace("{userAgent}", getContextData(ctx, INTERNAL_USER_AGENT).toString())
                    .replace("{sourceUri}", getContextData(ctx, INTERNAL_SOURCE_URI).toString())
                    .replace("{proxyUrl}", getContextData(ctx, INTERNAL_PROXY_URL).toString())
                    .replace("{statusCode}", getContextData(ctx, INTERNAL_STATUS_CODE).toString())
                    .replace("{serverHttpVersion}", getContextData(ctx, INTERNAL_SERVER_HTTP_VERSION).toString())
                    .replace("{clientHttpVersion}", getContextData(ctx, INTERNAL_CLIENT_HTTP_VERSION).toString())
                    .replace("{serverRemoteAddr}", getContextData(ctx, INTERNAL_SERVER_REMOTE_ADDR).toString())
                    .replace("{serverLocalAddr}", getContextData(ctx, INTERNAL_SERVER_LOCAL_ADDR).toString())
                    .replace("{clientLocalAddr}", getContextData(ctx, INTERNAL_CLIENT_LOCAL_ADDR).toString())
                    .replace("{clientRemoteAddr}", getContextData(ctx, INTERNAL_CLIENT_REMOTE_ADDR).toString())
                    .replace("{consumedMills}", String.valueOf(System.currentTimeMillis() - (Long) getContextData(ctx, INTERNAL_SEND_TIMESTAMP)));
            log.info(logInfo);
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
                log.error("{} start failed", name, ar.cause());

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
        return addRoute(proxyRoute, null, true);
    }

    public ReverseHttpProxy addRoute(ProxyRoute proxyRoute, Integer order) {
        return addRoute(proxyRoute, order, true);
    }

    /**
     * 添加路由
     *
     * @param proxyRoute 路由信息
     * @param order      order越小，优先级越高
     * @param printLog   true表示打印日志
     * @return 实例本身
     */
    public ReverseHttpProxy addRoute(
            ProxyRoute proxyRoute,
            Integer order,
            boolean printLog
    ) {
        Route route = router.route(proxyRoute.getSourceUrl()).setName(proxyRoute.getName());
        if (order != null) {
            route.order(order);
        }
        Map<String, String> map = proxyRoute.toMap();
        for (String key : map.keySet()) {
            setRouteMetadata(route, key, map.get(key));
        }
        String targetUrl = proxyRoute.getTargetUrl();
        if (targetUrl.startsWith(STATIC)) {
            String staticPath = targetUrl.replace(STATIC, "");
            // https://github.com/vert-x3/vertx-web/issues/204
            StaticHandler staticHandler = StaticHandler.create(FileSystemAccess.ROOT, staticPath)
                    .setDirectoryListing(false)
                    .setAlwaysAsyncFS(true)
                    .setIndexPage("index.html");
            route.handler(staticHandler);
        } else {
            route.handler(routingContextHandler(httpClient));
        }
        if (printLog) {
            jsonLog(proxyRoute);
        }
        return this;
    }

    protected void jsonLog(ProxyRoute proxyRoute) {
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


    /**
     * 将标头转为小写后，判断是否是逐跳标头
     * 时间复杂度为 O(1)
     *
     * @param headerName 标头名称
     * @return 是否是逐跳标头
     */
    protected boolean isHopByHopHeader(String headerName) {
        return headerName != null && HOP_BY_HOP_HEADERS_SET.contains(headerName.toLowerCase());
    }


    /**
     * 复制请求头。复制的过程中忽略逐跳标头
     *
     * @param ctx      路由上下文
     * @param realReq  真实请求
     * @param proxyReq 代理请求
     */
    protected void copyRequestHeaders(RoutingContext ctx, HttpServerRequest realReq, HttpClientRequest proxyReq) {
        proxyReq.headers().clear();
        for (String headerName : realReq.headers().names()) {
            // 若是逐跳标头，则跳过
            if (isHopByHopHeader(headerName)) {
                continue;
            }
            // 针对Host请求头进行忽略
            if ("host".equalsIgnoreCase(headerName)) {
                continue;
            }
            proxyReq.putHeader(headerName, realReq.headers().get(headerName));
        }

        // 传递真实客户端信息
        if (getContextData(ctx, P_FORWARD_IP) != null && Boolean.parseBoolean(getContextData(ctx, P_FORWARD_IP).toString())) {
            String firstForward = realReq.getHeader("X-Forwarded-For");
            String secondForward = realReq.remoteAddress() == null ? null : realReq.remoteAddress().hostAddress();
            String forward = firstForward == null ? secondForward : firstForward + ", " + secondForward;
            proxyReq.putHeader("X-Forwarded-For", forward);
            proxyReq.putHeader("X-Forwarded-Proto", realReq.scheme());
        }
        // 传递代理主机Host
        if (getContextData(ctx, P_PRESERVE_HOST) != null && Boolean.parseBoolean(getContextData(ctx, P_PRESERVE_HOST).toString())) {
            proxyReq.putHeader("Host", realReq.headers().get("Host"));
        }
        // 控制实际代理请求的长连接
        if (getContextData(ctx, P_HTTP_KEEPALIVE) != null && Boolean.parseBoolean(getContextData(ctx, P_HTTP_KEEPALIVE).toString())) {
            proxyReq.putHeader("Connection", "keep-alive");
        } else {
            proxyReq.putHeader("Connection", "close");
        }
    }

    /**
     * 复制响应头。复制的过程中忽略逐跳标头
     *
     * @param ctx       路由上下文
     * @param realReq   真实请求
     * @param realResp  真实响应
     * @param proxyResp 代理响应
     */
    protected void copyResponseHeaders(RoutingContext ctx, HttpServerRequest realReq, HttpServerResponse realResp, HttpClientResponse proxyResp) {
        realResp.headers().clear();

        Map<String, String> needSetHeaderMap = new LinkedHashMap<>();
        for (String headerName : proxyResp.headers().names()) {
            // 若是逐跳标头，则跳过
            if (isHopByHopHeader(headerName)) {
                continue;
            }
            // 保留Cookie
            if ("Set-Cookie".equalsIgnoreCase(headerName) || "Set-Cookie2".equalsIgnoreCase(headerName)) {
                if (getContextData(ctx, P_PRESERVE_COOKIES) != null && Boolean.parseBoolean(getContextData(ctx, P_PRESERVE_COOKIES).toString())) {
                    needSetHeaderMap.put(headerName, proxyResp.headers().get(headerName));
                }
            }
            // 重写重定向Location
            else if ("Location".equalsIgnoreCase(headerName)) {
                String value = rewriteLocation(ctx, realReq.absoluteURI(), proxyResp.headers().get(headerName));
                needSetHeaderMap.put(headerName, value);
            } else {
                needSetHeaderMap.put(headerName, proxyResp.headers().get(headerName));
            }
        }
        // 跨域由代理掌控
        if (getContextData(ctx, P_CORS_CONTROL) != null && Boolean.parseBoolean(getContextData(ctx, P_CORS_CONTROL).toString())) {
            // 允许跨域
            if (getContextData(ctx, P_ALLOW_CORS) != null && Boolean.parseBoolean(getContextData(ctx, P_ALLOW_CORS).toString())) {
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
            realResp.headers().set(key, needSetHeaderMap.get(key));
        }
    }

    /**
     * 重写Location
     *
     * @param ctx      路由上下文
     * @param url      原始URL
     * @param location 重定向位置
     * @return 重写后的Location
     */
    protected String rewriteLocation(RoutingContext ctx, String url, String location) {
        // 若重定向的地址，在反向代理的范围内，则进行重写
        String targetUrl = getContextData(ctx, P_TARGET_URL).toString();
        if (location != null && location.startsWith(targetUrl)) {
            UrlParser.ParsedUrl parsedUrl = UrlParser.parseUrl(url);
            String locationUri = location.replace(targetUrl, "");
            return parsedUrl.getFormatHostPort() + (getContextData(ctx, P_SOURCE_URL).toString().replace("/*", "")) + locationUri;
        }
        return location;
    }


    /**
     * 发起请求Handler
     *
     * @param ctx        路由上下文
     * @param serverReq  服务端请求
     * @param serverResp 服务端响应
     * @param proxyUrl   代理URL
     * @return 异步处理Handler
     */
    protected Handler<AsyncResult<HttpClientResponse>> sendRequestHandler(RoutingContext ctx, HttpServerRequest serverReq, HttpServerResponse serverResp, String proxyUrl) {
        return ar -> {
            if (ar.succeeded()) {
                HttpClientResponse clientResp = ar.result();
                // 暂停流读取
                clientResp.pause();
                // 复制响应头。复制的过程中忽略逐跳标头
                copyResponseHeaders(ctx, serverReq, serverResp, clientResp);
                if (!serverResp.headers().contains("Content-Length")) {
                    serverResp.setChunked(true);
                }
                // 设置响应码
                setStatusCode(ctx, serverResp, clientResp.statusCode());

                if ((boolean) getContextData(ctx, INTERNAL_CLIENT_CONNECTION_OPEN) && (boolean) getContextData(ctx, INTERNAL_SERVER_CONNECTION_OPEN)) {
                    // 流输出
                    clientResp.pipeTo(serverResp).onComplete(ar1 -> {
                        if (ar1.succeeded()) {
                            doLog(ctx);
                        } else {
                            badGateway(ctx, serverResp);
                            log.error("pipeTo failed. {} <-- {} <-- {} <-- {}",
                                    getContextData(ctx, INTERNAL_SERVER_REMOTE_ADDR),
                                    getContextData(ctx, INTERNAL_SERVER_LOCAL_ADDR),
                                    getContextData(ctx, INTERNAL_CLIENT_LOCAL_ADDR),
                                    getContextData(ctx, INTERNAL_CLIENT_REMOTE_ADDR),
                                    ar1.cause());
                        }
                    });
                }

            } else {
                badGateway(ctx, serverResp);
                log.error("{} {} send request error", serverReq.method().name(), proxyUrl, ar.cause());
            }
        };
    }

    /**
     * 建立连接Handler
     *
     * @param ctx        路由上下文
     * @param serverReq  服务端请求
     * @param serverResp 服务端响应
     * @param proxyUrl   代理URL
     * @return 异步处理Handler
     */
    protected Handler<AsyncResult<HttpClientRequest>> connectHandler(RoutingContext ctx, HttpServerRequest serverReq, HttpServerResponse serverResp, String proxyUrl) {
        return ar -> {
            if (ar.succeeded()) {
                HttpClientRequest clientReq = ar.result();
                setContextData(ctx, INTERNAL_CLIENT_HTTP_VERSION, clientReq.version().alpnName());
                // 记录连接状态
                setContextData(ctx, INTERNAL_CLIENT_CONNECTION_OPEN, true);

                // 注册客户端与代理服务之间连接的断开监听事件。可监听主动关闭和被动关闭
                HttpConnection connection = clientReq.connection();
                setContextData(ctx, INTERNAL_CLIENT_LOCAL_ADDR, connection.localAddress().toString());
                setContextData(ctx, INTERNAL_CLIENT_REMOTE_ADDR, connection.remoteAddress().toString());
                log.debug("target {} -- {} connected", getContextData(ctx, INTERNAL_CLIENT_LOCAL_ADDR), getContextData(ctx, INTERNAL_CLIENT_REMOTE_ADDR));

                connection.closeHandler(v -> {
                    setContextData(ctx, INTERNAL_CLIENT_CONNECTION_OPEN, false);
                    log.debug("target {} -- {} closed", getContextData(ctx, INTERNAL_CLIENT_LOCAL_ADDR), getContextData(ctx, INTERNAL_CLIENT_REMOTE_ADDR));
                });


                // 复制请求头。复制的过程中忽略逐跳标头
                copyRequestHeaders(ctx, serverReq, clientReq);

                if ((boolean) getContextData(ctx, INTERNAL_CLIENT_CONNECTION_OPEN) && (boolean) getContextData(ctx, INTERNAL_SERVER_CONNECTION_OPEN)) {
                    // bug: https://github.com/meethigher/tcp-reverse-proxy/issues/13
                    // 解决办法: 不管是否有请求体，都直接send pipeto。
                    clientReq.send(serverReq).onComplete(sendRequestHandler(ctx, serverReq, serverResp, proxyUrl));
                }
            } else {
                badGateway(ctx, serverResp);
                log.error("{} {} open connection error", serverReq.method().name(), proxyUrl, ar.cause());
            }

        };
    }

    protected void badGateway(RoutingContext ctx, HttpServerResponse serverResp) {
        if (!serverResp.ended()) {
            setStatusCode(ctx, serverResp, 502).end("Bad Gateway");
        }
        doLog(ctx);
    }


    /**
     * 路由处理Handler
     *
     * @param httpClient HTTP客户端
     * @return 路由处理Handler
     */
    protected Handler<RoutingContext> routingContextHandler(HttpClient httpClient) {
        return ctx -> {
            // 暂停流读取
            ctx.request().pause();

            HttpConnection connection = ctx.request().connection();
            setContextData(ctx, INTERNAL_SERVER_REMOTE_ADDR, connection.remoteAddress().toString());
            setContextData(ctx, INTERNAL_SERVER_LOCAL_ADDR, connection.localAddress().toString());
            // 记录请求开始时间
            setContextData(ctx, INTERNAL_SEND_TIMESTAMP, System.currentTimeMillis());
            // 记录连接状态
            setContextData(ctx, INTERNAL_SERVER_CONNECTION_OPEN, true);
            log.debug("source {} -- {} connected", getContextData(ctx, INTERNAL_SERVER_LOCAL_ADDR), getContextData(ctx, INTERNAL_SERVER_REMOTE_ADDR));

            // vertx的uri()是包含query参数的。而path()才是我们常说的不带有query的uri
            // route不是线程安全的。route里的metadata应以路由为单元存储，而不是以请求为单元存储。一个路由会有很多请求。
            // 若想要以请求为单元存储数据，应该使用routingContext.put
            // 将路由原数据，复制到请求上下文
            for (String key : ctx.currentRoute().metadata().keySet()) {
                setContextData(ctx, key, ctx.currentRoute().getMetadata(key));
            }

            // 获取代理地址
            String proxyUrl = getProxyUrl(ctx, ctx.request(), ctx.response());
            setContextData(ctx, INTERNAL_PROXY_URL, proxyUrl);
            setContextData(ctx, INTERNAL_SERVER_HTTP_VERSION, ctx.request().version().alpnName());
            setContextData(ctx, INTERNAL_METHOD, ctx.request().method().name());
            setContextData(ctx, INTERNAL_USER_AGENT, ctx.request().getHeader("User-Agent"));
            setContextData(ctx, INTERNAL_SOURCE_URI, ctx.request().uri());


            // 构建请求参数
            RequestOptions requestOptions = new RequestOptions();
            requestOptions.setAbsoluteURI(proxyUrl);
            requestOptions.setMethod(ctx.request().method());
            requestOptions.setFollowRedirects(getContextData(ctx, P_FOLLOW_REDIRECTS) != null && Boolean.parseBoolean(getContextData(ctx, P_FOLLOW_REDIRECTS).toString()));

            connection.closeHandler(v -> {
                setContextData(ctx, INTERNAL_SERVER_CONNECTION_OPEN, false);
                log.debug("source {} -- {} closed", getContextData(ctx, INTERNAL_SERVER_LOCAL_ADDR), getContextData(ctx, INTERNAL_SERVER_REMOTE_ADDR));
            });

            // 如果跨域由代理服务接管，那么针对跨域使用的OPTIONS预检请求，就由代理服务接管，而不经过实际的后端服务
            if (HttpMethod.OPTIONS.name().equalsIgnoreCase(ctx.request().method().name()) &&
                    getContextData(ctx, P_CORS_CONTROL) != null && Boolean.parseBoolean(getContextData(ctx, P_CORS_CONTROL).toString()) &&
                    getContextData(ctx, P_ALLOW_CORS) != null && Boolean.parseBoolean(getContextData(ctx, P_ALLOW_CORS).toString())
            ) {
                String header = ctx.request().getHeader("origin");
                if (header == null || header.isEmpty()) {
                    ctx.response().putHeader("Access-Control-Allow-Origin", "*");
                } else {
                    ctx.response().putHeader("Access-Control-Allow-Origin", header);
                }
                ctx.response().putHeader("Access-Control-Allow-Methods", "*");
                ctx.response().putHeader("Access-Control-Allow-Headers", "*");
                ctx.response().putHeader("Access-Control-Allow-Credentials", "true");
                ctx.response().putHeader("Access-Control-Expose-Headers", "*");
                setStatusCode(ctx, ctx.response(), 200).end();
                doLog(ctx);
                return;
            }

            // 请求
            if ((boolean) getContextData(ctx, INTERNAL_SERVER_CONNECTION_OPEN)) {
                httpClient.request(requestOptions).onComplete(connectHandler(ctx, ctx.request(), ctx.response(), proxyUrl));
            }
        };
    }

    /**
     * 获取代理后的完整proxyUrl，不区分代理目标路径是否以/结尾。
     * 处理逻辑为删除掉匹配的路径，并将剩下的内容追加到代理目标路径后面。
     *
     * @param ctx        路由上下文
     * @param serverReq  服务端请求
     * @param serverResp 服务端响应
     * @return 代理后的完整URL
     */
    protected String getProxyUrl(RoutingContext ctx, HttpServerRequest serverReq, HttpServerResponse serverResp) {
        String targetUrl = getContextData(ctx, P_TARGET_URL).toString();
        // 不区分targetUrl是否以/结尾，均以targetUrl不带/来处理
        if (targetUrl.endsWith("/")) {
            targetUrl = targetUrl.substring(0, targetUrl.length() - 1);
        }


        // 在vertx中，uri表示hostPort后面带有参数的地址。而这里的uri表示不带有参数的地址。
        final String uri = serverReq.path();

        //final String params = serverReq.uri().replace(uri, "");
        final String params = fastReplace(serverReq.uri(), uri, "");


        // 若不是多级匹配，则直接代理到目标地址。注意要带上请求参数
        if (!getContextData(ctx, P_SOURCE_URL).toString().endsWith("*")) {
            return targetUrl + params;
        }

        String matchedUri = ctx.currentRoute().getPath();
        if (matchedUri.endsWith("/")) {
            matchedUri = matchedUri.substring(0, matchedUri.length() - 1);
        }

        //String suffixUri = uri.replace(matchedUri, "");
        String suffixUri = fastReplace(uri, matchedUri, "");

        // 代理路径尾部与用户初始请求保持一致
        if (uri.endsWith("/") && !suffixUri.endsWith("/")) {
            suffixUri = suffixUri + "/";
        }
        if (!uri.endsWith("/") && suffixUri.endsWith("/")) {
            suffixUri = suffixUri.substring(0, suffixUri.length() - 1);
        }

        // 因为targetUrl后面不带/，因此后缀需要以/开头
        if (!suffixUri.isEmpty() && !suffixUri.startsWith("/")) {
            suffixUri = "/" + suffixUri;
        }

        return targetUrl + suffixUri + params;
    }


}

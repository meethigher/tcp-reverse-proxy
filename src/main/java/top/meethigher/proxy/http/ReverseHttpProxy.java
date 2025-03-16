package top.meethigher.proxy.http;

import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
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


    /**
     * 默认的日志格式
     */
    public static final String LOG_FORMAT_DEFAULT = "" +
            "{name} -- " +
            "{serverHttpVersion} -- " +
            "{clientHttpVersion} -- " +
            "{method} -- " +
            "{userAgent} -- " +
            "{serverRemoteAddr} -- " +
            "{clientLocalAddr} -- " +
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
    protected static final String INTERNAL_CLIENT_CONNECTION_OPEN = "INTERNAL_CLIENT_CONNECTION_OPEN";

    /**
     * 连接状态：代理服务--后端服务
     */
    protected static final String INTERNAL_PROXY_SERVER_CONNECTION_OPEN = "INTERNAL_PROXY_SERVER_CONNECTION_OPEN";

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
     * 代理服务发起请求的本端地址
     */
    protected static final String INTERNAL_CLIENT_LOCAL_ADDR = "INTERNAL_CLIENT_LOCAL_ADDR";


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
        final String prefix = "ReverseHttpProxy-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger("top.meethigher.proxy.http.ReverseHttpProxy.name", 0) + 1);
                System.setProperty("top.meethigher.proxy.http.ReverseHttpProxy.name", next);
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
     */
    protected void setContextData(RoutingContext ctx, String key, Object value) {
        ctx.put(key, value == null ? "" : value);
    }

    protected Object getContextData(RoutingContext ctx, String key) {
        Object metadata = ctx.get(key);
        return metadata == null ? "" : metadata;
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
                    .replace("{serverHttpVersion}", getContextData(ctx, INTERNAL_SERVER_HTTP_VERSION).toString())
                    .replace("{clientHttpVersion}", getContextData(ctx, INTERNAL_CLIENT_HTTP_VERSION).toString())
                    .replace("{method}", getContextData(ctx, INTERNAL_METHOD).toString())
                    .replace("{userAgent}", getContextData(ctx, INTERNAL_USER_AGENT).toString())
                    .replace("{serverRemoteAddr}", getContextData(ctx, INTERNAL_SERVER_REMOTE_ADDR).toString())
                    .replace("{clientLocalAddr}", getContextData(ctx, INTERNAL_CLIENT_LOCAL_ADDR).toString())
                    .replace("{sourceUri}", getContextData(ctx, INTERNAL_SOURCE_URI).toString())
                    .replace("{proxyUrl}", getContextData(ctx, INTERNAL_PROXY_URL).toString())
                    .replace("{statusCode}", getContextData(ctx, INTERNAL_STATUS_CODE).toString())
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
            setRouteMetadata(route, key, map.get(key));
        }
        route.handler(routingContextHandler(httpClient));
        jsonLog(proxyRoute);
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
     */
    protected boolean isHopByHopHeader(String headerName) {
        return headerName != null && HOP_BY_HOP_HEADERS_SET.contains(headerName.toLowerCase());
    }


    /**
     * 复制请求头。复制的过程中忽略逐跳标头
     */
    protected void copyRequestHeaders(RoutingContext ctx, HttpServerRequest realReq, HttpClientRequest proxyReq) {
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
     */
    protected void copyResponseHeaders(RoutingContext ctx, HttpServerRequest realReq, HttpServerResponse realResp, HttpClientResponse proxyResp) {
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
                if (getContextData(ctx, P_PRESERVE_COOKIES) != null && Boolean.parseBoolean(getContextData(ctx, P_PRESERVE_COOKIES).toString())) {
                    needSetHeaderMap.put(headerName, proxyHeaders.get(headerName));
                }
            }
            // 重写重定向Location
            else if ("Location".equalsIgnoreCase(headerName)) {
                String value = rewriteLocation(ctx, realReq.absoluteURI(), proxyHeaders.get(headerName));
                needSetHeaderMap.put(headerName, value);
            } else {
                needSetHeaderMap.put(headerName, proxyHeaders.get(headerName));
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
            realHeaders.set(key, needSetHeaderMap.get(key));
        }
    }

    /**
     * 重写Location
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

                if ((boolean) getContextData(ctx, INTERNAL_PROXY_SERVER_CONNECTION_OPEN) && (boolean) getContextData(ctx, INTERNAL_CLIENT_CONNECTION_OPEN)) {
                    // 流输出
                    clientResp.pipeTo(serverResp).onSuccess(v -> {
                        doLog(ctx);
                    }).onFailure(e -> {
                        badGateway(ctx, serverResp);
                        log.error("{} {} clientResp pipeto serverResp error", serverReq.method().name(), proxyUrl, e);
                    });
                }

            } else {
                badGateway(ctx, serverResp);
                Throwable e = ar.cause();
                log.error("{} {} send request error", serverReq.method().name(), proxyUrl, e);
            }
        };
    }

    /**
     * 建立连接Handler
     */
    protected Handler<AsyncResult<HttpClientRequest>> connectHandler(RoutingContext ctx, HttpServerRequest serverReq, HttpServerResponse serverResp, String proxyUrl) {
        return ar -> {
            if (ar.succeeded()) {
                HttpClientRequest clientReq = ar.result();
                setContextData(ctx, INTERNAL_CLIENT_HTTP_VERSION, clientReq.version().alpnName());
                // 记录连接状态
                setContextData(ctx, INTERNAL_PROXY_SERVER_CONNECTION_OPEN, true);

                // 注册客户端与代理服务之间连接的断开监听事件。可监听主动关闭和被动关闭
                HttpConnection connection = clientReq.connection();
                SocketAddress localAddress = connection.localAddress();
                setContextData(ctx, INTERNAL_CLIENT_LOCAL_ADDR, localAddress.hostAddress() + ":" + localAddress.port());
                connection.closeHandler(v -> {
                    setContextData(ctx, INTERNAL_PROXY_SERVER_CONNECTION_OPEN, false);
                    log.debug("proxyClient local connection {} closed",
                            getContextData(ctx, INTERNAL_CLIENT_LOCAL_ADDR).toString());
                });


                // 复制请求头。复制的过程中忽略逐跳标头
                copyRequestHeaders(ctx, serverReq, clientReq);

                if ((boolean) getContextData(ctx, INTERNAL_PROXY_SERVER_CONNECTION_OPEN) && (boolean) getContextData(ctx, INTERNAL_CLIENT_CONNECTION_OPEN)) {
                    // 若存在请求体，则将请求体复制。使用流式复制，避免占用大量内存
                    if (clientReq.headers().contains("Content-Length") || clientReq.headers().contains("Transfer-Encoding")) {
                        clientReq.send(serverReq).onComplete(sendRequestHandler(ctx, serverReq, serverResp, proxyUrl));
                    } else {
                        clientReq.send().onComplete(sendRequestHandler(ctx, serverReq, serverResp, proxyUrl));
                    }
                } else if ((boolean) getContextData(ctx, INTERNAL_PROXY_SERVER_CONNECTION_OPEN) && !(boolean) getContextData(ctx, INTERNAL_CLIENT_CONNECTION_OPEN)) {
                    // 整体链路连接不可用，释放资源
                    connection.close();
                }
            } else {
                badGateway(ctx, serverResp);
                Throwable e = ar.cause();
                log.error("{} {} open connection error", serverReq.method().name(), proxyUrl, e);
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
     */
    protected Handler<RoutingContext> routingContextHandler(HttpClient httpClient) {
        return ctx -> {
            // vertx的uri()是包含query参数的。而path()才是我们常说的不带有query的uri
            // route不是线程安全的。route里的metadata应以路由为单元存储，而不是以请求为单元存储。一个路由会有很多请求。
            // 若想要以请求为单元存储数据，应该使用routingContext.put
            Route route = ctx.currentRoute();
            // 将路由原数据，复制到请求上下文
            for (String key : route.metadata().keySet()) {
                setContextData(ctx, key, route.getMetadata(key));
            }

            // 记录请求开始时间
            setContextData(ctx, INTERNAL_SEND_TIMESTAMP, System.currentTimeMillis());
            // 记录连接状态
            setContextData(ctx, INTERNAL_CLIENT_CONNECTION_OPEN, true);


            HttpServerRequest serverReq = ctx.request();
            HttpServerResponse serverResp = ctx.response();

            // 暂停流读取
            serverReq.pause();


            // 获取代理地址
            String proxyUrl = getProxyUrl(ctx, serverReq, serverResp);
            setContextData(ctx, INTERNAL_PROXY_URL, proxyUrl);
            setContextData(ctx, INTERNAL_SERVER_HTTP_VERSION, serverReq.version().alpnName());
            setContextData(ctx, INTERNAL_METHOD, serverReq.method().name());
            setContextData(ctx, INTERNAL_USER_AGENT, serverReq.getHeader("User-Agent"));
            setContextData(ctx, INTERNAL_SOURCE_URI, serverReq.uri());


            // 构建请求参数
            RequestOptions requestOptions = new RequestOptions();
            requestOptions.setAbsoluteURI(proxyUrl);
            requestOptions.setMethod(serverReq.method());
            requestOptions.setFollowRedirects(getContextData(ctx, P_FOLLOW_REDIRECTS) != null && Boolean.parseBoolean(getContextData(ctx, P_FOLLOW_REDIRECTS).toString()));


            // 注册客户端与代理服务之间连接的断开监听事件。可监听主动关闭和被动关闭
            HttpConnection connection = serverReq.connection();
            SocketAddress remoteAddress = connection.remoteAddress();
            setContextData(ctx, INTERNAL_SERVER_REMOTE_ADDR, remoteAddress.hostAddress() + ":" + remoteAddress.port());

            connection.closeHandler(v -> {
                setContextData(ctx, INTERNAL_CLIENT_CONNECTION_OPEN, false);
                log.debug("proxyServer remote connection {} closed", getContextData(ctx, INTERNAL_SERVER_REMOTE_ADDR).toString());
            });

            // 如果跨域由代理服务接管，那么针对跨域使用的OPTIONS预检请求，就由代理服务接管，而不经过实际的后端服务
            if (HttpMethod.OPTIONS.name().equalsIgnoreCase(serverReq.method().name()) &&
                    getContextData(ctx, P_CORS_CONTROL) != null && Boolean.parseBoolean(getContextData(ctx, P_CORS_CONTROL).toString()) &&
                    getContextData(ctx, P_ALLOW_CORS) != null && Boolean.parseBoolean(getContextData(ctx, P_ALLOW_CORS).toString())
            ) {
                String header = serverReq.getHeader("origin");
                if (header == null || header.isEmpty()) {
                    serverResp.putHeader("Access-Control-Allow-Origin", "*");
                } else {
                    serverResp.putHeader("Access-Control-Allow-Origin", header);
                }
                serverResp.putHeader("Access-Control-Allow-Methods", "*");
                serverResp.putHeader("Access-Control-Allow-Headers", "*");
                serverResp.putHeader("Access-Control-Allow-Credentials", "true");
                serverResp.putHeader("Access-Control-Expose-Headers", "*");
                setStatusCode(ctx, serverResp, 200).end();
                doLog(ctx);
                return;
            }

            // 请求
            if ((boolean) getContextData(ctx, INTERNAL_CLIENT_CONNECTION_OPEN)) {
                httpClient.request(requestOptions).onComplete(connectHandler(ctx, serverReq, serverResp, proxyUrl));
            }
        };
    }

    /**
     * 获取代理后的完整proxyUrl，不区分代理目标路径是否以/结尾。
     * 处理逻辑为删除掉匹配的路径，并将剩下的内容追加到代理目标路径后面。
     */
    protected String getProxyUrl(RoutingContext ctx, HttpServerRequest serverReq, HttpServerResponse serverResp) {
        String targetUrl = getContextData(ctx, P_TARGET_URL).toString();
        // 不区分targetUrl是否以/结尾，均以targetUrl不带/来处理
        if (targetUrl.endsWith("/")) {
            targetUrl = targetUrl.substring(0, targetUrl.length() - 1);
        }


        // 在vertx中，uri表示hostPort后面带有参数的地址。而这里的uri表示不带有参数的地址。
        final String uri = serverReq.path();
        final String params = serverReq.uri().replace(uri, "");


        // 若不是多级匹配，则直接代理到目标地址。注意要带上请求参数
        if (!getContextData(ctx, P_SOURCE_URL).toString().endsWith("*")) {
            return targetUrl + params;
        }

        String matchedUri = ctx.currentRoute().getPath();
        if (matchedUri.endsWith("/")) {
            matchedUri = matchedUri.substring(0, matchedUri.length() - 1);
        }
        String suffixUri = uri.replace(matchedUri, "");

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

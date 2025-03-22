package top.meethigher.proxy.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.ext.web.Router;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class ReverseHttpProxyTest {


    private static final Logger log = LoggerFactory.getLogger(ReverseHttpProxyTest.class);

    private static final Vertx vertx = Vertx.vertx();

    /**
     * 用于测试一系列常规的HTTP反代请求
     * 可以配置batch-curl.sh进行使用
     */
    @Test
    public void testAllMethod() throws Exception {
        ReverseHttpProxy proxy = ReverseHttpProxy.create(
                vertx
        );
        proxy.port(8080);
        proxy.start();
        ProxyRoute proxyRoute = new ProxyRoute();
        proxyRoute.setHttpKeepAlive(false);
        proxyRoute.setSourceUrl("/*");
        proxyRoute.setTargetUrl("https://reqres.in");
        proxy.addRoute(proxyRoute);
        TimeUnit.HOURS.sleep(1);
        proxy.stop();
    }

    @Test
    public void testStatic() throws Exception {
        ReverseHttpProxy proxy = ReverseHttpProxy.create(
                vertx
        );
        proxy.port(8080);
        proxy.start();
        ProxyRoute proxyRoute = new ProxyRoute();
        proxyRoute.setHttpKeepAlive(false);
//        proxyRoute.setSourceUrl("/*");
//        proxyRoute.setTargetUrl("static:D:/Desktop");
        proxyRoute.setSourceUrl("/blog/*");
        proxyRoute.setTargetUrl("static:D:/3Develop/www/hexoBlog/blog/public");
        proxy.addRoute(proxyRoute);
        TimeUnit.HOURS.sleep(1);
        proxy.stop();
    }


    /**
     * 用于测试省略端口时的反代场景
     */
    @Test
    public void testDomain80() throws Exception {
        ReverseHttpProxy proxy = ReverseHttpProxy.create(
                Router.router(vertx),
                vertx.createHttpServer(),
                vertx.createHttpClient(new PoolOptions().setHttp1MaxSize(2000).setHttp2MaxSize(2000))
        );
        proxy.port(80);
        proxy.start();
        ProxyRoute proxyRoute = new ProxyRoute();
        proxyRoute.setSourceUrl("/*");
        proxyRoute.setTargetUrl("https://webst01.is.autonavi.com");
        proxyRoute.setHttpKeepAlive(false);
        proxyRoute.setName("proxy");
        proxy.addRoute(proxyRoute);
        TimeUnit.HOURS.sleep(1);
        proxy.stop();
    }

    /**
     * 用来测试Vertx的route拦截规则。
     * 结论
     * 像一些特殊的路由，比如/route/、/route，即可以匹配/route/*，也可以匹配/route，就看是谁的优先级更高了
     */
    @Test
    public void testVertxRoute() throws Exception {
        Router router = Router.router(vertx);
        router.route("/route/*").handler(ctx -> {
            ctx.response().end("/route/*");
        });
        router.route("/route").order(Integer.MIN_VALUE).handler(ctx -> {
            ctx.response().end("/route");
        });
        vertx.createHttpServer().requestHandler(router).listen(666);

        LockSupport.park();
    }


    /**
     * 解决bug
     * https://github.com/meethigher/tcp-reverse-proxy/issues/1
     * <p>
     * 首先需要理解vertx的route匹配规则
     *
     * @see ReverseHttpProxyTest#testVertxRoute()
     */
    @Test
    public void testProxyUrl1() throws Exception {
        // 启动后端
        vertx.createHttpServer().requestHandler(req -> {
            req.response().end(req.absoluteURI());
        }).listen(888);
        // 启动代理
        ReverseHttpProxy.create(vertx)
                .addRoute(new ProxyRoute()
                        .setSourceUrl("/*")
                        .setTargetUrl("http://127.0.0.1:888"), 0)
                .addRoute(new ProxyRoute()
                        .setSourceUrl("/route/*")
                        .setTargetUrl("http://10.0.0.1:888/route"), -1)
                .addRoute(new ProxyRoute()
                        .setSourceUrl("/route/static")
                        .setTargetUrl("http://localhost:888/route/static"), -2
                )
                .port(8080).start();

        // 测试用例
        // key为测试样例，value为正确返回结果
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("http://127.0.0.1:8080", "http://127.0.0.1:888/");
        cases.put("http://127.0.0.1:8080/", "http://127.0.0.1:888/");
        cases.put("http://127.0.0.1:8080/test/", "http://127.0.0.1:888/test/");
        cases.put("http://127.0.0.1:8080/test", "http://127.0.0.1:888/test");
        cases.put("http://127.0.0.1:8080/route/static", "http://localhost:888/route/static");
        cases.put("http://127.0.0.1:8080/route/1", "http://10.0.0.1:888/route/1");
        cases.put("http://127.0.0.1:8080/route/", "http://10.0.0.1:888/route/");
        cases.put("http://127.0.0.1:8080/route", "http://10.0.0.1:888/route");
        cases.put("http://127.0.0.1:8080/route?name=1&age=1", "http://10.0.0.1:888/route?name=1&age=1");

        proxyUrlCommon(cases);


    }

    /**
     * 本机ip有127.0.0.1、10.0.0.1
     */
    @Test
    public void testProxyUrl2() throws Exception {
        // 启动后端
        vertx.createHttpServer().requestHandler(req -> {
            req.response().end(req.absoluteURI());
        }).listen(888);
        // 启动代理
        ReverseHttpProxy.create(vertx)
                .addRoute(new ProxyRoute()
                        .setSourceUrl("/*")
                        .setTargetUrl("http://127.0.0.1:888"), Integer.MAX_VALUE)
                .addRoute(new ProxyRoute()
                        .setSourceUrl("/api/*")
                        .setTargetUrl("http://10.0.0.1:888"), 2)
                .addRoute(new ProxyRoute()
                        .setSourceUrl("/service/*")
                        .setTargetUrl("http://10.0.0.1:888/api"), 1)
                .addRoute(new ProxyRoute()
                        .setSourceUrl("/specific")
                        .setTargetUrl("http://10.0.0.1:888"), 1)
                .addRoute(new ProxyRoute()
                        .setSourceUrl("/static/")
                        .setTargetUrl("http://10.0.0.1:888"), 2)
                .port(8080).start();

        // 测试用例
        // key为测试样例，value为正确返回结果
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("http://127.0.0.1:8080/", "http://127.0.0.1:888/");
        cases.put("http://127.0.0.1:8080/test", "http://127.0.0.1:888/test");
        cases.put("http://127.0.0.1:8080/test/", "http://127.0.0.1:888/test/");
        cases.put("http://127.0.0.1:8080/path?x=1&y=2", "http://127.0.0.1:888/path?x=1&y=2");

        cases.put("http://127.0.0.1:8080/api/", "http://10.0.0.1:888/");
        cases.put("http://127.0.0.1:8080/api/v1/resource ", "http://10.0.0.1:888/v1/resource");
        cases.put("http://127.0.0.1:8080/api/v2?param=abc", "http://10.0.0.1:888/v2?param=abc");


        cases.put("http://127.0.0.1:8080/service/", "http://10.0.0.1:888/api/");
        cases.put("http://127.0.0.1:8080/service/user/info", "http://10.0.0.1:888/api/user/info");
        cases.put("http://127.0.0.1:8080/service/query?id=42", "http://10.0.0.1:888/api/query?id=42");
        cases.put("http://127.0.0.1:8080/service/details?x=1&y=2", "http://10.0.0.1:888/api/details?x=1&y=2");


        // 注意这种直接以端口结尾的，实际都是在端口后面加了一级/
        cases.put("http://127.0.0.1:8080/specific", "http://10.0.0.1:888/");
        cases.put("http://127.0.0.1:8080/specific?a=1", "http://10.0.0.1:888/?a=1");
        cases.put("http://127.0.0.1:8080/static/", "http://10.0.0.1:888/");
        cases.put("http://127.0.0.1:8080/static/img", "http://127.0.0.1:888/static/img");

        proxyUrlCommon(cases);
    }

    private void proxyUrlCommon(Map<String, String> cases) throws InterruptedException, ExecutionException {
        HttpClient httpClient = Vertx.vertx().createHttpClient();

        boolean actual = true;

        for (String key : cases.keySet()) {
            HttpClientRequest httpClientRequest = httpClient.request(new RequestOptions().setMethod(HttpMethod.GET).setAbsoluteURI(key))
                    .toCompletionStage()  // 转换为 CompletionStage
                    .toCompletableFuture() // 转换为 CompletableFuture
                    .get();// 阻塞等待完成
            HttpClientResponse httpClientResponse = httpClientRequest.send().toCompletionStage().toCompletableFuture().get();
            String result = httpClientResponse.body().toCompletionStage().toCompletableFuture().get().toString();
            String s = cases.get(key);
            System.out.println("请求地址: " + key + "\n实际结果: " + result + "\n预期结果: " + s);
            System.out.println(s.equals(result));
            System.out.println("===============");
            if (actual && !s.equals(result)) {
                actual = false;
            }
        }

        Assert.assertTrue(actual);
    }


    /**
     * 代理服务开启h2c（HTTP/2 cleartext）
     * 后端服务也支持http2
     */
    @Test
    public void testServerHttp2Proxy() throws Exception {
        Router router = Router.router(vertx);
        HttpClientOptions httpClientOptions = new HttpClientOptions()
                .setTrustAll(true)
                .setVerifyHost(false)
                // httpclient支持与后端服务进行协商使用使用http2
                .setUseAlpn(true)
                .setProtocolVersion(HttpVersion.HTTP_2);
        HttpServerOptions httpServerOptions = new HttpServerOptions()
                // 服务端支持与客户端进行协商，使用h2c（HTTP/2 cleartext）
                // 常规情况下，h2只在开启了tls使用。如果不开启tls，需要指定使用的是h2c
                .setAlpnVersions(Collections.unmodifiableList(Arrays.asList(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2)))
                .setUseAlpn(true)
                .setHttp2ClearTextEnabled(true);

        HttpServer httpServer = vertx.createHttpServer(httpServerOptions);
        HttpClient httpClient = vertx.createHttpClient(httpClientOptions);

        ReverseHttpProxy.create(router, httpServer, httpClient)
                .addRoute(new ProxyRoute()
                        .setSourceUrl("/*")
                        .setTargetUrl("https://reqres.in"))
                .port(8080)
                .start();

        LockSupport.park();
    }

    @Test
    public void testVertxDnsResolve() {
        // 比较okhttp与vertx http的dns解析。发现vertx特别慢。因此定位问题
        // 复现代码参考https://github.com/meethigher/bug-test/tree/vertx-http-dns
    }
}
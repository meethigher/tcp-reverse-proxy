package top.meethigher.proxy.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.ext.web.Router;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

}
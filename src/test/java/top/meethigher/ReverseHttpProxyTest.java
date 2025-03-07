package top.meethigher;

import io.vertx.core.Vertx;
import io.vertx.core.http.PoolOptions;
import io.vertx.ext.web.Router;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.http.ProxyRoute;
import top.meethigher.proxy.http.ReverseHttpProxy;

import java.util.concurrent.TimeUnit;

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

}
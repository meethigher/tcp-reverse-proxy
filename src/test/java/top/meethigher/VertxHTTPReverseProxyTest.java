package top.meethigher;

import io.vertx.core.Vertx;
import org.junit.Test;
import top.meethigher.proxy.http.ProxyRoute;
import top.meethigher.proxy.http.VertxHTTPReverseProxy;

import java.util.concurrent.TimeUnit;

public class VertxHTTPReverseProxyTest {

    @Test
    public void testVertxHTTPReverseProxyTest() throws Exception {
        VertxHTTPReverseProxy proxy = VertxHTTPReverseProxy.create(Vertx.vertx());

        proxy.start();
        ProxyRoute proxyRoute = new ProxyRoute();
        proxyRoute.setSourceUrl("/api/*");
        proxyRoute.setTargetUrl("https://reqres.in/");
        proxy.addRoute(proxyRoute);
        TimeUnit.MINUTES.sleep(20);
        proxy.stop();
    }

}
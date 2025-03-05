package top.meethigher;

import io.vertx.core.Vertx;
import org.junit.Test;
import top.meethigher.proxy.http.ProxyRoute;
import top.meethigher.proxy.http.ReverseHttpProxy;

import java.util.concurrent.TimeUnit;

public class ReverseHttpProxyTest {

    @Test
    public void testVertxHTTPReverseProxyTest() throws Exception {
        Vertx vertx = Vertx.vertx();
        ReverseHttpProxy proxy = ReverseHttpProxy.create(
                vertx
        );
        proxy.port(8080);
        proxy.start();
        ProxyRoute proxyRoute = new ProxyRoute();
        proxyRoute.setSourceUrl("/test/*");
        proxyRoute.setTargetUrl("https://meethigher.top");
        proxy.addRoute(proxyRoute);
        TimeUnit.MINUTES.sleep(2);
        proxy.stop();
    }

}
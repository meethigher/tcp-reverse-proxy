package top.meethigher;

import io.vertx.core.Vertx;
import org.junit.Test;
import top.meethigher.proxy.http.VertxHTTPReverseProxy;

import java.util.concurrent.TimeUnit;

public class VertxHTTPReverseProxyTest {

    @Test
    public void testVertxHTTPReverseProxyTest() throws Exception {
        VertxHTTPReverseProxy proxy = VertxHTTPReverseProxy.create(Vertx.vertx());
        proxy.addRoute(null);
        proxy.removeRoute(null);
        proxy.getRoutes();
        proxy.enableRoute(null);
        proxy.disableRoute(null);
        proxy.start();

        TimeUnit.SECONDS.sleep(20);
        proxy.stop();
    }

}
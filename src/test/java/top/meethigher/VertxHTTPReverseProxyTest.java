package top.meethigher;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.web.Router;
import org.junit.Test;
import top.meethigher.proxy.http.ProxyRoute;
import top.meethigher.proxy.http.VertxHTTPReverseProxy;

import java.util.concurrent.TimeUnit;

public class VertxHTTPReverseProxyTest {

    @Test
    public void testVertxHTTPReverseProxyTest() throws Exception {
        Vertx vertx = Vertx.vertx();
        VertxHTTPReverseProxy proxy = VertxHTTPReverseProxy.create(
                Router.router(vertx),
                vertx.createHttpServer(),
                vertx.createHttpClient(new HttpClientOptions().setVerifyHost(false).setTrustAll(true))
        );
        proxy.port(998);
        proxy.start();
        ProxyRoute proxyRoute = new ProxyRoute();
        proxyRoute.setSourceUrl("/*");
        proxyRoute.setTargetUrl("https://10.0.0.10:4321");
        proxy.addRoute(proxyRoute);
        TimeUnit.MINUTES.sleep(20);
        proxy.stop();
    }

}
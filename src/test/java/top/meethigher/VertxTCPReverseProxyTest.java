package top.meethigher;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class VertxTCPReverseProxyTest {

    @Test
    public void testVertxTCPReverseProxy() throws Exception {
        VertxTCPReverseProxy proxy = VertxTCPReverseProxy.create(Vertx.vertx(), "10.0.0.9", 5432);
        proxy.port(22).start();
        TimeUnit.MINUTES.sleep(10);
        proxy.stop();
    }

}
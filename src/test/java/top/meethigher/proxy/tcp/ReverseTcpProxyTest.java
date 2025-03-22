package top.meethigher.proxy.tcp;

import io.vertx.core.Vertx;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ReverseTcpProxyTest {

    @Test
    public void testVertxTCPReverseProxy() throws Exception {
        ReverseTcpProxy proxy = ReverseTcpProxy.create(Vertx.vertx(), "meethigher.top", 443);
        proxy.port(8080).start();
        TimeUnit.MINUTES.sleep(10);
        proxy.stop();
    }

}
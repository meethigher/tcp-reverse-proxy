package top.meethigher.proxy.tcp;

import io.vertx.core.Vertx;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ReverseTcpProxyTest {

    @Test
    public void testVertxTCPReverseProxy() throws Exception {
        ReverseTcpProxy proxy = ReverseTcpProxy.create(Vertx.vertx(), "10.0.0.1", 888);
        proxy.port(8080).start();
        TimeUnit.MINUTES.sleep(10);
        proxy.stop();
    }

}
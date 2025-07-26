package top.meethigher.proxy.tcp;

import io.vertx.core.Vertx;
import org.junit.Test;
import top.meethigher.proxy.http.ProxyRoute;
import top.meethigher.proxy.http.ReverseHttpProxy;

import java.util.concurrent.locks.LockSupport;

public class Issue18Test {

    @Test
    public void tcp() {
        // 使用一个不通的端口，这样延迟pipeto，进而实现延迟注册exceptionHandler
        ReverseTcpProxy.create(Vertx.vertx(), "127.0.0.1", 9443)
                .port(8080)
                .start();

        LockSupport.park();
    }

    @Test
    public void http() {
        ReverseHttpProxy.create(Vertx.vertx())
                .port(8080)
                .addRoute(new ProxyRoute().setName("hh")
                        .setSourceUrl("/*")
                        .setTargetUrl("http://127.0.0.1:9443/api"))
                .start();
        LockSupport.park();
    }
}

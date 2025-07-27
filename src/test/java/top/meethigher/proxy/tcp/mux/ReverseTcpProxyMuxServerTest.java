package top.meethigher.proxy.tcp.mux;

import io.vertx.core.Vertx;
import org.junit.Test;

import java.util.concurrent.locks.LockSupport;

public class ReverseTcpProxyMuxServerTest {

    @Test
    public void name() {
        ReverseTcpProxyMuxServer.create(Vertx.vertx())
                .port(8080)
                .start();

        LockSupport.park();
    }
}
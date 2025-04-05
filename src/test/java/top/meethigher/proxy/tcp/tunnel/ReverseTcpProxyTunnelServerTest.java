package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class ReverseTcpProxyTunnelServerTest {


    @Test
    public void server() {
        Vertx vertx = Vertx.vertx(new VertxOptions().setMaxEventLoopExecuteTime(Duration.ofDays(1).toNanos()));
        // 设置空闲超时，注意该超时参数，应该大于客户端的心跳时间
        NetServer netServer = vertx.createNetServer(new NetServerOptions().setIdleTimeout(10).setIdleTimeoutUnit(TimeUnit.SECONDS));
        ReverseTcpProxyTunnelServer.create(vertx, netServer)
                .start();
        LockSupport.park();
    }
}
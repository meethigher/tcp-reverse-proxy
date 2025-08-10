package top.meethigher.proxy.tcp;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import org.junit.Test;
import top.meethigher.proxy.NetAddress;
import top.meethigher.proxy.RoundRobinLoadBalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class ReverseTcpProxyTest {

    @Test
    public void testVertxTCPReverseProxy() throws Exception {
        ReverseTcpProxy proxy = ReverseTcpProxy.create(Vertx.vertx(), "meethigher.top", 443);
        proxy.port(8080).start();
        TimeUnit.MINUTES.sleep(10);
        proxy.stop();
    }


    @Test
    public void testLb() throws Exception {
        Vertx vertx = Vertx.vertx();
        NetServer netServer = vertx.createNetServer();
        NetClient netClient = vertx.createNetClient();
        List<NetAddress> list = new ArrayList<>();
        list.add(new NetAddress("10.0.0.20", 22));
        list.add(new NetAddress("10.0.0.30", 22));
        ReverseTcpProxy.create(netServer, netClient, RoundRobinLoadBalancer.create(list), ReverseTcpProxy.generateName())
                .start();

        TimeUnit.SECONDS.sleep(10);

        list.add(new NetAddress("meethigher.top", 80));

        LockSupport.park();
    }
}
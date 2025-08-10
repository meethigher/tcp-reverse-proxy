package top.meethigher.proxy.udp;

import io.vertx.core.Vertx;
import org.junit.Test;
import top.meethigher.proxy.NetAddress;
import top.meethigher.proxy.RoundRobinLoadBalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class ReverseUdpProxyTest {

    @Test
    public void name() throws Exception {
        List<NetAddress> list = new ArrayList<>();

        list.add(new NetAddress("119.29.29.29", 53));
        list.add(new NetAddress("time.windows.com", 123));


        RoundRobinLoadBalancer lb = RoundRobinLoadBalancer.create(list);
        ReverseUdpProxy.create(Vertx.vertx(), lb)
                .port(123)
                .start();

        TimeUnit.SECONDS.sleep(2);

        lb.all().add(new NetAddress("10.0.0.1", 123));


        LockSupport.park();
    }
}
package top.meethigher.proxy.tcp;

import org.junit.Test;
import top.meethigher.proxy.NetAddress;
import top.meethigher.proxy.RoundRobinLoadBalancer;

import java.util.ArrayList;
import java.util.List;

public class RoundRobinLoadBalancerTest {

    @Test
    public void next() {
        List<NetAddress> nodes = new ArrayList<>();
        nodes.add(new NetAddress("127.0.0.1", 6666));
        nodes.add(new NetAddress("127.0.0.1", 6667));
        RoundRobinLoadBalancer balancer = RoundRobinLoadBalancer.create(nodes);
        System.out.println(balancer.next());
        System.out.println(balancer.next());
        System.out.println(balancer.next());
        nodes.add(new NetAddress("127.0.0.1", 6668));
        System.out.println(balancer.next());
        System.out.println(balancer.next());
        System.out.println(balancer.next());
    }
}
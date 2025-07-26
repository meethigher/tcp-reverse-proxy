package top.meethigher.proxy.tcp;

import top.meethigher.proxy.LoadBalancer;
import top.meethigher.proxy.NetAddress;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询策略实现
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/07/26 13:41
 */
public class TcpRoundRobinLoadBalancer implements LoadBalancer<NetAddress> {

    private final List<NetAddress> nodes;

    private final AtomicInteger idx = new AtomicInteger(0);

    private final String name = "TcpRoundRobinLoadBalancer";

    private TcpRoundRobinLoadBalancer(List<NetAddress> nodes) {
        this.nodes = nodes;
    }


    public NetAddress next() {
        if (nodes == null) {
            return null;
        }
        int index = idx.getAndUpdate(v -> (v + 1) % nodes.size());
        return nodes.get(index);
    }

    @Override
    public String name() {
        return name;
    }

    public static TcpRoundRobinLoadBalancer create(List<NetAddress> nodes) {
        return new TcpRoundRobinLoadBalancer(nodes);
    }
}

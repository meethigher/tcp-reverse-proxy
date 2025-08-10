package top.meethigher.proxy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询策略实现
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/07/26 13:41
 */
public class RoundRobinLoadBalancer implements LoadBalancer<NetAddress> {

    private final List<NetAddress> nodes;

    private final AtomicInteger idx = new AtomicInteger(0);

    private final String name;

    private RoundRobinLoadBalancer(List<NetAddress> nodes) {
        if (nodes.size() <= 0) {
            throw new IllegalStateException("nodes size must be greater than 0");
        }
        this.nodes = nodes;
        this.name = RoundRobinLoadBalancer.class.getSimpleName();
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

    @Override
    public List<NetAddress> all() {
        return nodes;
    }

    public static RoundRobinLoadBalancer create(List<NetAddress> nodes) {
        return new RoundRobinLoadBalancer(nodes);
    }
}

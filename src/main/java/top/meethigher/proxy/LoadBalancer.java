package top.meethigher.proxy;

/**
 * 负载均衡策略
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/07/26 13:05
 */
public interface LoadBalancer<T> {
    T next();

    String name();
}

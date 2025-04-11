package top.meethigher.proxy.tcp.tunnel.utils;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 四字节长度编码生成器
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/04/05 17:56
 */
public class IdGenerator {
    // 初始值可以设为 1
    private static final AtomicInteger counter = new AtomicInteger(1);

    /**
     * 获取一个 4 字节的唯一编号
     * 超过 Integer.MAX_VALUE 后从 1 重新开始（跳过负数）
     */
    public static int nextId() {
        int id = counter.getAndIncrement();
        // 避免超过 Integer.MAX_VALUE 后变成负数
        if (id < 0) {
            synchronized (counter) {
                if (counter.get() < 0) {
                    counter.set(1);
                    id = counter.getAndIncrement();
                }
            }
        }
        return id;
    }
}

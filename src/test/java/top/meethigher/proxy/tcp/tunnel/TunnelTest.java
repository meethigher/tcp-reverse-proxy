package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * 该内容参考https://github.com/meethigher/tcp-reverse-proxy/issues/7
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/05/11 13:01
 */
public class TunnelTest {

    private static final Logger log = LoggerFactory.getLogger(TunnelTest.class);

    @Test
    public void t() throws Exception {
        /**
         * 内网穿透实际大批量使用时，未配对的用户连接堆积，导致后续新来的用户连接等待配对的耗时越来越长。
         */
        Vertx vertx = Vertx.vertx();
        NetClient netClient = vertx.createNetClient();
        int initialValue = 100;
        int batchReq = 10;

        AtomicInteger ai = new AtomicInteger(1);

        for (int i = 0; i < initialValue; i++) {
            final String id = ai.getAndIncrement() + "";
            netClient.connect(2222, "10.0.0.1").onComplete(ar -> {
                if (ar.succeeded()) {
                    NetSocket socket = ar.result();
                    long start = System.currentTimeMillis();
                    socket.pause();
                    socket.handler(buf -> {
                        log.info("{} consumed {} ms, response: {}", id, System.currentTimeMillis() - start, buf.toString());
                    });
                    socket.closeHandler(t -> {
                        log.info("{} consumed {} ms, closed", id, System.currentTimeMillis() - start);
                    });
                    socket.resume();
                } else {
                    log.error("{}", id, ar.cause());
                }
            });
        }

        LockSupport.park();
    }

}

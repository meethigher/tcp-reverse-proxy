package top.meethigher.proxy.tcp;

import io.vertx.core.Vertx;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

public class NetSocketWriteFailureTest {

    private static final Logger log = LoggerFactory.getLogger(NetSocketWriteFailureTest.class);

    @Test
    public void step1() {
        Vertx vertx = Vertx.vertx();
        vertx.createNetServer()
                .connectHandler(src -> {
                    vertx.setTimer(3000, id -> {
                        /**
                         * 不能使用write的成功与否判断链路是否正常，但是可以通过write.onSuccess保证顺序写入。
                         * 测试中发现，即便链路异常，返回仍然是true
                         *
                         * write是把数据复制到缓冲区，缓冲区有空间一般就不会失败
                         */
                        src.write(UUID.randomUUID().toString()).onComplete(ar -> {
                            log.info("server -> client write result: {}", ar.succeeded());
                        });
                    });
                })
                .exceptionHandler(e -> {
                    log.error("socket errors happening before the connection is passed to the connectHandler", e);
                })
                .listen(8080).onFailure(e -> {
                    System.exit(1);
                });


        LockSupport.park();

    }

    @Test
    public void step2() {
        Vertx vertx = Vertx.vertx();
        vertx.createNetClient().connect(8080, "127.0.0.1").onSuccess(src -> {
            System.exit(1);
        });

        LockSupport.park();
    }
}

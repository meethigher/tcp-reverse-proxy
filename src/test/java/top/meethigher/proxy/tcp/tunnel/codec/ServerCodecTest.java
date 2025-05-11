package top.meethigher.proxy.tcp.tunnel.codec;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import org.junit.Test;
import top.meethigher.proxy.tcp.tunnel.proto.TunnelMessage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * 用于定位问题https://github.com/meethigher/tcp-reverse-proxy/issues/7
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/05/11 17:22
 */
public class ServerCodecTest {

    @Test
    public void server() {

        ExecutorService executorService = Executors.newFixedThreadPool(10);

        Vertx vertx = Vertx.vertx();
        NetServer netServer = vertx.createNetServer();
        netServer.connectHandler(socket -> {
            socket.pause();
            for (int i = 0; i < 100; i++) {
                executorService.execute(new MultiThreadWrite(socket, vertx));
            }
            socket.resume();
        }).listen(8080).onFailure(e -> {
            e.printStackTrace();
            System.exit(1);
        });
        LockSupport.park();
    }

    public static class MultiThreadWrite implements Runnable {

        private static final AtomicInteger ai = new AtomicInteger(0);

        private final NetSocket socket;

        private final Vertx vertx;

        public MultiThreadWrite(NetSocket socket, Vertx vertx) {
            this.socket = socket;
            this.vertx = vertx;
        }

        @Override
        public void run() {
            final int i = ai.incrementAndGet();
            TunnelMessage.OpenDataPort test = TunnelMessage.OpenDataPort.newBuilder()
                    .setSecret(i + "-hello world").build();
            Buffer buffer = TunnelMessageCodec.encode(TunnelMessageType.OPEN_DATA_PORT.code(), test.toByteArray());
            socket.write(buffer).onComplete(ar -> {
                System.out.println(i + "-->open data port, result:" + ar.succeeded());
            });
        }
    }
}

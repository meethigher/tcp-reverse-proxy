package top.meethigher.proxy.tcp.tunnel.codec;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.net.NetSocket;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.TunnelClient;
import top.meethigher.proxy.tcp.tunnel.handler.AbstractTunnelHandler;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * 用于定位问题https://github.com/meethigher/tcp-reverse-proxy/issues/7
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/05/11 17:22
 */
public class ClientCodecTest {

    private static final Logger log = LoggerFactory.getLogger(ClientCodecTest.class);

    @Test
    public void client() {
        Vertx vertx = Vertx.vertx(new VertxOptions().setMaxWorkerExecuteTimeUnit(TimeUnit.DAYS));
        TunnelClient tunnelClient = new TunnelClient(vertx, vertx.createNetClient(), 1000, 64000) {

        };
        tunnelClient.connect("127.0.0.1", 8080);
        tunnelClient.on(TunnelMessageType.OPEN_DATA_PORT, new AbstractTunnelHandler() {
            @Override
            protected boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes) {
                log.info("<--open_data_port: {}", new String(bodyBytes, StandardCharsets.UTF_8));
                return false;
            }
        });

        LockSupport.park();
    }
}

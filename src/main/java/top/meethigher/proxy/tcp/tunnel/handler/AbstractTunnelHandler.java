package top.meethigher.proxy.tcp.tunnel.handler;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;

import java.util.concurrent.Callable;


/**
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/04/05 01:47
 */
public abstract class AbstractTunnelHandler implements TunnelHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractTunnelHandler.class);

    @Override
    public void handle(Vertx vertx, NetSocket netSocket, Buffer buffer) {
        // 避免将任务丢到 eventloop 里面
        vertx.executeBlocking((Callable<Void>) () -> {
            TunnelMessageCodec.DecodedMessage decodedMessage = TunnelMessageCodec.decode(buffer);
            TunnelMessageType type = TunnelMessageType.fromCode(decodedMessage.type);
            log.debug("received message type = {} from {}, doHandle ...", type, netSocket.remoteAddress());
            boolean result = doHandle(vertx, netSocket, type, decodedMessage.body);
            log.debug("received message type = {} from {}, doHandle result = {}", type, netSocket.remoteAddress(), result);
            return null;
        });
    }

    /**
     * 执行逻辑，并返回执行结果。true表示成功
     * 
     * @param vertx 用于执行异步操作的Vertx实例
     * @param netSocket 网络连接Socket
     * @param type 消息类型
     * @param bodyBytes 消息体字节数组
     * @return 处理结果，true表示成功，false表示失败
     */
    protected abstract boolean doHandle(Vertx vertx, NetSocket netSocket, TunnelMessageType type, byte[] bodyBytes);
}

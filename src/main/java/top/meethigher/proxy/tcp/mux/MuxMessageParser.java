package top.meethigher.proxy.tcp.mux;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageParser;

/**
 * 适用于TcpMux的通信消息解析器
 * 只解析第一条数据
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/07/27 02:19
 */
public class MuxMessageParser extends TunnelMessageParser {

    public static final class MuxMessage {
        // muxclient发送给muxserver的backendServer配置信息
        public final Buffer backendServerBuf;
        // user发给muxclient的信息
        public final Buffer payload;

        public MuxMessage(Buffer backendServerBuf, Buffer payload) {
            this.backendServerBuf = backendServerBuf;
            this.payload = payload;
        }
    }


    private static final Logger log = LoggerFactory.getLogger(MuxMessageParser.class);

    protected final Handler<MuxMessage> muxMessageHandler;

    public MuxMessageParser(Handler<MuxMessage> muxMessageHandler, NetSocket netSocket) {
        // 用不到这个父级的handler，传null即可
        super(null, netSocket);
        this.muxMessageHandler = muxMessageHandler;
    }


    @Override
    protected void parse() {
        // 获取消息的预设总长度
        int totalLength = buf.getInt(lengthFieldOffset);
        // 校验消息预设总长度是否超过最大限制
        if (totalLength > maxLength) {
            log.warn("too many bytes in length field, {} > {}, connection {} -- {} will be closed",
                    totalLength, maxLength,
                    netSocket.localAddress(),
                    netSocket.remoteAddress());
            netSocket.close();
            return;
        }
        // 校验预设总长度
        if (buf.length() < totalLength) {
            return;
        }
        // 校验类型编码是否在预设范围内
        if (totalLength >= (lengthFieldLength + typeFieldLength)) {
            short code = buf.getShort(lengthFieldLength);
            if (Mux.type == code) {

            } else {
                log.warn("invalid type, connection {} -- {} will be closed",
                        netSocket.localAddress(),
                        netSocket.remoteAddress());
                netSocket.close();
                return;
            }

        }
        muxMessageHandler.handle(new MuxMessage(buf.getBuffer(0, totalLength), buf.getBuffer(totalLength, buf.length())));
    }
}

package top.meethigher.proxy.tcp.tunnel.codec;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自定义消息结构解析器
 * [4字节长度+2字节类型+protobuf变长消息体]
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/04/04 02:52
 */
public class TunnelMessageParser implements Handler<Buffer> {

    private static final Logger log = LoggerFactory.getLogger(TunnelMessageParser.class);
    private Buffer buf = Buffer.buffer();

    /**
     * 最大长度，单位字节。防止对方构造超长字段，占用内存。
     */
    private final int maxLength = 1024 * 1024;

    /**
     * 预设长度起始位置
     */
    private final int lengthFieldOffset = 0;

    /**
     * 预设长度占用的字节数
     */
    private final int lengthFieldLength = 4;
    /**
     * 消息类型起始位置
     */
    private final int typeFieldOffset = 4;

    /**
     * 消息类型占用的字节数
     */
    private final int typeFieldLength = 2;

    /**
     * 消息体起始位置
     */
    private final int bodyFieldOffset = 6;

    private final Handler<Buffer> outputHandler;

    private final NetSocket netSocket;

    public TunnelMessageParser(Handler<Buffer> outputHandler,
                               NetSocket netSocket) {
        this.outputHandler = outputHandler;
        this.netSocket = netSocket;
    }

    @Override
    public void handle(Buffer buffer) {
        buf.appendBuffer(buffer);
        if (buf.length() < lengthFieldLength) {
            return;
        } else {
            int totalLength = buf.getInt(lengthFieldOffset);
            // 校验最大长度
            if (totalLength > maxLength) {
                log.warn("{} > {}, too many bytes in length field, connection {} will be closed",
                        totalLength, maxLength,
                        netSocket.remoteAddress());
                netSocket.close();
                return;
            }
            // 校验类型编码是否在预设范围内
            if (totalLength >= (lengthFieldLength + typeFieldLength)) {
                short code = buf.getShort(lengthFieldLength);
                try {
                    TunnelMessageType.fromCode(code);
                } catch (Exception e) {
                    log.error("invalid type, connection {} will be closed", netSocket.remoteAddress(), e);
                    netSocket.close();
                    return;
                }
            }

            // 校验是否达到预设总长度
            if (buf.length() < totalLength) {
                return;
            } else {
                outputHandler.handle(buf.getBuffer(0, totalLength));
                buf = buf.getBuffer(totalLength, buf.length());
            }
        }

    }
}

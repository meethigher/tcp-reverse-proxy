package top.meethigher.proxy.tcp.tunnel.codec;

import io.vertx.core.buffer.Buffer;

/**
 * 自定义消息编解码
 * <pre>
 * |  4 字节（消息长度） |  2 字节（消息类型） |  变长（消息体）  |
 * |      0x0010      |      0x0001       |   {"id":1, "msg":"Hello"}  |
 * </pre>
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/04/04 23:12
 */
public class TunnelMessageCodec {


    public static Buffer encode(short type, byte[] body) {
        int totalLength = 4 + 2 + body.length;
        Buffer buffer = Buffer.buffer();
        buffer.appendInt(totalLength);
        buffer.appendShort(type);
        buffer.appendBytes(body);
        return buffer;
    }

    public static DecodedMessage decode(Buffer buffer) {
        int totalLength = buffer.getInt(0);
        short type = buffer.getShort(4);
        byte[] body = buffer.getBytes(6, buffer.length());
        return new DecodedMessage(totalLength, type, body);
    }

    public static class DecodedMessage {
        public final int totalLength;
        public final short type;
        public final byte[] body;

        public DecodedMessage(int totalLength, short type, byte[] body) {
            this.totalLength = totalLength;
            this.type = type;
            this.body = body;
        }
    }


}

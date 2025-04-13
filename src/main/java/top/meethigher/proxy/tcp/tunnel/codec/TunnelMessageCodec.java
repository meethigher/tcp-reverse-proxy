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


    /**
     * 将消息类型和消息体编码为Buffer
     * 
     * @param type 消息类型，2字节
     * @param body 消息体字节数组
     * @return 编码后的Buffer，格式为：4字节消息长度 + 2字节消息类型 + 变长消息体
     */
    public static Buffer encode(short type, byte[] body) {
        int totalLength = 4 + 2 + body.length;
        Buffer buffer = Buffer.buffer();
        buffer.appendInt(totalLength);
        buffer.appendShort(type);
        buffer.appendBytes(body);
        return buffer;
    }

    /**
     * 将Buffer解码为DecodedMessage对象
     * 
     * @param buffer 待解码的Buffer，格式为：4字节消息长度 + 2字节消息类型 + 变长消息体
     * @return 解码后的DecodedMessage对象，包含总长度、消息类型和消息体
     */
    public static DecodedMessage decode(Buffer buffer) {
        int totalLength = buffer.getInt(0);
        short type = buffer.getShort(4);
        byte[] body = buffer.getBytes(6, buffer.length());
        return new DecodedMessage(totalLength, type, body);
    }

    /**
     * 解码后的消息对象，包含总长度、消息类型和消息体
     */
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

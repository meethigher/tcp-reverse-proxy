package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageParser;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.handler.TunnelHandler;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static top.meethigher.proxy.FastAes.*;

/**
 * 封装通用的 TCP 交互API
 * 参考<a href="https://github.com/socketio/socket.io-client-java/blob/socket.io-client-2.1.0/src/main/java/io/socket/client/Socket.java">socket.io-client-java</a>
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/04/05 02:10
 */
public abstract class Tunnel {

    private static final Logger log = LoggerFactory.getLogger(Tunnel.class);
    protected final Vertx vertx;
    protected final String secret;
    public static final String SECRET_DEFAULT = "hello,meethigher";

    // 数据连接建立后立马发送4字节标识符cafebabe
    public static final byte[] DATA_CONN_FLAG = new byte[]{
            (byte) 0xca,
            (byte) 0xfe,
            (byte) 0xba,
            (byte) 0xbe
    };

    protected Tunnel(Vertx vertx, String secret) {
        this.vertx = vertx;
        this.secret = secret;
    }

    /**
     * 监听消息类型 {@code TunnelMessageType} 以及触发的动作 {@code TunnelHandler}
     */
    protected Map<TunnelMessageType, TunnelHandler> tunnelHandlers = new LinkedHashMap<>();

    /**
     * 监听socket连接成功触发的动作
     *
     * @param tunnelHandler 连接成功后处理逻辑
     */
    public void onConnected(TunnelHandler tunnelHandler) {
        tunnelHandlers.put(null, tunnelHandler);
    }

    /**
     * 监听 {@code TunnelMessageType} 事件
     *
     * @param type          消息类型
     * @param tunnelHandler 处理逻辑
     */
    public void on(TunnelMessageType type, TunnelHandler tunnelHandler) {
        tunnelHandlers.put(type, tunnelHandler);
    }


    /**
     * 返回加密base64串(无换行)
     * @param bodyBytes 原文
     * @return 密文
     */
    public byte[] aesBase64Encode(byte[] bodyBytes) {
        SecretKey key = restoreKey(secret.getBytes(StandardCharsets.UTF_8));
        return encrypt(bodyBytes, key);
    }

    /**
     * 将加密内容还原
     *
     * @param bodyBytes 密文
     * @return 原文
     */
    public byte[] aesBase64Decode(byte[] bodyBytes) {
        SecretKey key = restoreKey(secret.getBytes(StandardCharsets.UTF_8));
        return decrypt(bodyBytes, key);
    }

    /**
     * 将请求体按照{@code TunnelMessageCodec} 规范进行编码
     *
     * @param type      消息类型
     * @param bodyBytes 请求体
     * @return 编码结果
     */
    public Buffer encode(TunnelMessageType type, byte[] bodyBytes) {
        return TunnelMessageCodec.encode(type.code(), aesBase64Encode(bodyBytes));
    }

    /**
     * 将请求体按照 {@code TunnelMessageCodec } 进行解码
     * <p>
     * 若传输过来的数据，不符合要求，就会将连接关闭，该操作适合 Server 端的安全防护，Client 端完全没必要这么严格。因此，Client 如果使用该方法的话，建议重写
     *
     * @param socket 连接
     * @return 解码器
     */
    public TunnelMessageParser decode(final NetSocket socket) {
        return new TunnelMessageParser(buffer -> {
            TunnelMessageCodec.DecodedMessage decodedMessage = TunnelMessageCodec.decode(buffer);
            TunnelMessageType type = TunnelMessageType.fromCode(decodedMessage.type);
            for (TunnelMessageType tunnelMessageType : tunnelHandlers.keySet()) {
                if (type == tunnelMessageType) {
                    TunnelHandler tunnelHandler = tunnelHandlers.get(tunnelMessageType);
                    if (tunnelHandler != null) {
                        tunnelHandler.handle(vertx, socket, buffer);
                    } else {
                        log.debug("no tunnel handler for {}, connection {} will be closed", type, socket.remoteAddress());
                        socket.close();
                    }
                    return;
                }
            }
            log.debug("no tunnel handler for {}, connection {} will be closed", type, socket.remoteAddress());
            socket.close();
            return;
        }, socket);
    }
}

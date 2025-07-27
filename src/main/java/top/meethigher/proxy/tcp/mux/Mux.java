package top.meethigher.proxy.tcp.mux;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.NetAddress;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static top.meethigher.proxy.FastAes.*;

/**
 * TCP Port Service Multiplexer (TCPMUX)
 * <p>
 * 单端口多路复用
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc1078">RFC 1078 - TCP port service Multiplexer (TCPMUX)</a>
 * @since 2025/07/26 16:18
 */
public abstract class Mux {

    private static final Logger log = LoggerFactory.getLogger(Mux.class);
    /**
     * 默认对称加密密钥
     */
    public static final String SECRET_DEFAULT = "1234567890abcdef";

    public static final short type = 5209;

    protected static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();


    protected final Vertx vertx;

    protected final String secret;


    public Mux(Vertx vertx, String secret) {
        this.vertx = vertx;
        this.secret = secret;
    }

    /**
     * 将host与port通过英文冒号连接，返回加密base64串(无换行)
     */
    public Buffer aesBase64Encode(NetAddress netAddress) {
        String addr = netAddress.toString();
        SecretKey key = restoreKey(secret.getBytes(StandardCharsets.UTF_8));
        String encryptedAddr = encryptToBase64(addr.getBytes(StandardCharsets.UTF_8), key);
        return TunnelMessageCodec.encode(type, encryptedAddr.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将加密内容还原
     */
    public NetAddress aesBase64Decode(Buffer buffer) {
        TunnelMessageCodec.DecodedMessage decode = TunnelMessageCodec.decode(buffer);
        String encryptedAddr = new String(decode.body, StandardCharsets.UTF_8);
        SecretKey key = restoreKey(secret.getBytes(StandardCharsets.UTF_8));
        String addr = new String(decryptFromBase64(encryptedAddr, key),
                StandardCharsets.UTF_8);
        return NetAddress.parse(addr);
    }


}

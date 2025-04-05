package top.meethigher.proxy.tcp.tunnel.codec;

import io.vertx.core.buffer.Buffer;
import org.junit.Assert;
import org.junit.Test;
import top.meethigher.proxy.tcp.tunnel.proto.TunnelMessage;

public class TunnelMessageCodecTest {
    @Test
    public void name() throws Exception {
        TunnelMessage.Auth auth = TunnelMessage.Auth.newBuilder()
                .setToken("你好，世界！").build();
        Buffer buffer = TunnelMessageCodec.encode(TunnelMessageType.AUTH.code(), auth.toByteArray());
        TunnelMessageCodec.DecodedMessage decodedMessage = TunnelMessageCodec.decode(buffer);
        TunnelMessage.Auth parsed = TunnelMessage.Auth.parseFrom(decodedMessage.body);
        System.out.println(TunnelMessageType.fromCode(decodedMessage.type));
        Assert.assertEquals(auth.getToken(), parsed.getToken());
    }
}
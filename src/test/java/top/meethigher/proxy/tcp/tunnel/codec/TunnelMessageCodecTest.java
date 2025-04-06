package top.meethigher.proxy.tcp.tunnel.codec;

import io.vertx.core.buffer.Buffer;
import org.junit.Assert;
import org.junit.Test;
import top.meethigher.proxy.tcp.tunnel.proto.TunnelMessage;

public class TunnelMessageCodecTest {
    @Test
    public void name() throws Exception {
        TunnelMessage.OpenDataPort auth = TunnelMessage.OpenDataPort.newBuilder()
                .setSecret("你好，世界！").build();
        Buffer buffer = TunnelMessageCodec.encode(TunnelMessageType.OPEN_DATA_PORT.code(), auth.toByteArray());
        TunnelMessageCodec.DecodedMessage decodedMessage = TunnelMessageCodec.decode(buffer);
        TunnelMessage.OpenDataPort parsed = TunnelMessage.OpenDataPort.parseFrom(decodedMessage.body);
        System.out.println(TunnelMessageType.fromCode(decodedMessage.type));
        Assert.assertEquals(auth.getSecret(), parsed.getSecret());
    }
}
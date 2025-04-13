package top.meethigher.proxy.tcp.tunnel.proto;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TunnelMessageTest {


    @Test
    public void test() throws Exception {
        TunnelMessage.OpenDataPort authRequest = TunnelMessage.OpenDataPort.newBuilder()
                .setSecret("你好，世界！").build();
        byte[] byteArray = authRequest.toByteArray();
        TunnelMessage.OpenDataPort parsed = TunnelMessage.OpenDataPort.parseFrom(byteArray);
        assertEquals(authRequest.getSecret(), parsed.getSecret());
    }
}
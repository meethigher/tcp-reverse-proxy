package top.meethigher.proxy.tcp.tunnel.proto;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TunnelMessageTest {


    @Test
    public void test() throws Exception {
        TunnelMessage.Auth authRequest = TunnelMessage.Auth.newBuilder()
                .setToken("你好，世界！").build();
        byte[] byteArray = authRequest.toByteArray();
        TunnelMessage.Auth parsed = TunnelMessage.Auth.parseFrom(byteArray);
        assertEquals(authRequest.getToken(), parsed.getToken());
    }
}
package top.meethigher.proxy.tcp.tunnel.codec;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class TunnelMessageParserTest {

    @Test
    public void handle() {

        Buffer buffer1 = TunnelMessageCodec.encode((short) 1, "first message".getBytes(StandardCharsets.UTF_8));
        Buffer buffer2 = TunnelMessageCodec.encode((short) 2, "second message".getBytes(StandardCharsets.UTF_8));
        Buffer buffer3 = TunnelMessageCodec.encode((short) 3, "third message".getBytes(StandardCharsets.UTF_8));
        Buffer buffer4 = TunnelMessageCodec.encode((short) 4, "forth message".getBytes(StandardCharsets.UTF_8));

        Buffer buffer = Buffer.buffer().appendBuffer(buffer1).appendBuffer(buffer2).appendBuffer(buffer3).appendBuffer(buffer4);
        Handler<Buffer> handler = buf -> {
            TunnelMessageCodec.DecodedMessage decodedMessage = TunnelMessageCodec.decode(buf);
            System.out.println(new String(decodedMessage.body));
        };

        TunnelMessageParser parser = new TunnelMessageParser(handler, null);

        parser.handle(buffer);
    }
}
package top.meethigher.proxy.tcp.tunnel.codec;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

public class TunnelMessageParserTest {

    @Test
    public void handle() {

        Buffer buffer1 = TunnelMessageCodec.encode((short) 1, "first message".getBytes(StandardCharsets.UTF_8));
        Buffer buffer2 = TunnelMessageCodec.encode((short) 2, "second message".getBytes(StandardCharsets.UTF_8));
        Buffer buffer3 = TunnelMessageCodec.encode((short) 3, "third message".getBytes(StandardCharsets.UTF_8));
        Buffer buffer4 = TunnelMessageCodec.encode((short) 4, "forth message".getBytes(StandardCharsets.UTF_8));

        final Buffer bufferOrigin = Buffer.buffer().appendBuffer(buffer1).appendBuffer(buffer2).appendBuffer(buffer3).appendBuffer(buffer4);
        Buffer buffer = bufferOrigin.copy();
        Handler<Buffer> handler = buf -> {
            TunnelMessageCodec.DecodedMessage decodedMessage = TunnelMessageCodec.decode(buf);
            System.out.println(new String(decodedMessage.body));
        };

        TunnelMessageParser parser = new TunnelMessageParser(handler, null);

        // 一条消息完整写出
        parser.handle(buffer);

        for (int i = 0; i < 10000; i++) {
            // 随机长度消息写出
            Buffer tBuf = bufferOrigin.copy();
            System.out.println("start " + tBuf.length());
            int index = 0;
            while (true) {
                int lastIndex = tBuf.length();
                if (index >= lastIndex) {
                    break;
                }
                int endIndex = ThreadLocalRandom.current().nextInt(index, lastIndex + 1);
                parser.handle(tBuf.getBuffer(index, endIndex));
                System.out.println(index + "--" + endIndex);
                index = endIndex;

            }
            System.out.println("end");
        }

    }
}
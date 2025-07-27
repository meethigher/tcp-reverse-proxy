package top.meethigher.proxy.tcp.mux;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import org.junit.Test;
import top.meethigher.proxy.NetAddress;

import java.util.concurrent.locks.LockSupport;

public class MuxMessageParserTest {


    /**
     * 我想验证，如果一次性发了一大串内容。RecordParser可以只读取前1个字节，就pause，当resume可以继续从第2个之后消费
     * <p>
     * 结论：不行
     */
    @Test
    public void testRecordParserAndPause() {
        Vertx vertx = Vertx.vertx();
        vertx.createNetServer().connectHandler(socket -> {
            socket.pause();
            RecordParser parser = RecordParser.newFixed(1);
            parser.handler(buf -> {
                System.out.println(buf);
                socket.pause();
                vertx.setTimer(5000, id -> {
                    socket.handler(buf1 -> {
                        System.out.println(buf1);
                    });
                    socket.resume();
                });
            });
            socket.handler(parser);
            socket.resume();
        }).listen(777).onFailure(e -> System.exit(1));

        NetClient netClient = vertx.createNetClient();
        netClient.connect(777, "127.0.0.1").onSuccess(socket -> {
            socket.write("123456789");
            vertx.setTimer(1000, id -> {
                socket.write("abcdefg");
            });
        });

        LockSupport.park();
    }

    @Test
    public void name() {

        NetSocket socket = null;

        Vertx vertx = Vertx.vertx();
        Mux mux = new Mux(vertx, Mux.SECRET_DEFAULT) {
        };
        NetAddress netAddress1 = new NetAddress("127.0.0.1", 8080);


        Buffer buffer1 = mux.aesBase64Encode(netAddress1);


        Buffer buffer2 = Buffer.buffer("halo wode");

        Buffer buffer = Buffer.buffer().appendBuffer(buffer1).appendBuffer(buffer2);


        MuxMessageParser parser = new MuxMessageParser(msg -> {
            System.out.println("控制消息：" + mux.aesBase64Decode(msg.backendServerBuf));
            System.out.println("用户消息：" + msg.payload);
        }, socket);

        parser.handle(buffer);


        LockSupport.park();
    }
}
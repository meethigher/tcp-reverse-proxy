package top.meethigher.proxy.tcp.mux;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.junit.Test;
import top.meethigher.proxy.NetAddress;

import java.util.ArrayList;
import java.util.List;

public class MuxTest {

    @Test
    public void name() {
        Mux mux = new Mux(Vertx.vertx(), Mux.SECRET_DEFAULT) {

        };

        NetAddress netAddress1 = new NetAddress("127.0.0.1", 8080);
        NetAddress netAddress2 = new NetAddress("127.0.0.1", 8081);

        List<NetAddress> list = new ArrayList<>();
        list.add(netAddress1);
        list.add(netAddress2);

        Buffer encode = mux.encode(list);

        List<NetAddress> decode = mux.decode(encode);
        System.out.println();
    }
}
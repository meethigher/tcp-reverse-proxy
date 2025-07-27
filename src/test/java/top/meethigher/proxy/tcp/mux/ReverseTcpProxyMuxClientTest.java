package top.meethigher.proxy.tcp.mux;

import io.vertx.core.Vertx;
import org.junit.Test;
import top.meethigher.proxy.NetAddress;
import top.meethigher.proxy.tcp.mux.model.MuxNetAddress;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class ReverseTcpProxyMuxClientTest {
    @Test
    public void name() throws Exception {
        Vertx vertx = Vertx.vertx();
        Map<MuxNetAddress, NetAddress> mapper = new LinkedHashMap<>();
        mapper.put(new MuxNetAddress("0.0.0.0", 6666, "ssh20"), new NetAddress("10.0.0.20", 22));
        mapper.put(new MuxNetAddress("0.0.0.0", 6667, "ssh30"), new NetAddress("10.0.0.30", 22));
        mapper.put(new MuxNetAddress("0.0.0.0", 6668, "http"), new NetAddress("reqres.in", 443));
        NetAddress muxServerAddress = new NetAddress("127.0.0.1", 8080);
        ReverseTcpProxyMuxClient client = ReverseTcpProxyMuxClient.create(vertx, mapper, muxServerAddress);
        client.start();

        TimeUnit.HOURS.sleep(2);

        client.stop();

        LockSupport.park();

    }
}
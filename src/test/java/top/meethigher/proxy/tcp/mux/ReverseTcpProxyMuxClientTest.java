package top.meethigher.proxy.tcp.mux;

import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class ReverseTcpProxyMuxClientTest {
    @Test
    public void name() throws Exception {
        ReverseTcpProxyMuxClient client = ReverseTcpProxyMuxClient.create();
        client.start();

        TimeUnit.HOURS.sleep(2);

        client.stop();

        LockSupport.park();

    }
}
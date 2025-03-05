package top.meethigher;

import org.junit.Test;
import top.meethigher.proxy.tcp.SimpleReverseTcpProxy;

import java.util.concurrent.TimeUnit;

public class SimpleReverseTcpProxyTest {


    @Test
    public void testSSH() throws Exception {
        SimpleReverseTcpProxy proxy = SimpleReverseTcpProxy.create("10.0.0.9", 22);
        proxy.port(11).start();
        TimeUnit.MINUTES.sleep(10);
        proxy.stop();
    }


    @Test
    public void testPostgreSQL() throws Exception {
        SimpleReverseTcpProxy proxy = SimpleReverseTcpProxy.create("10.0.0.9", 5432);
        proxy.port(22)
                .maxConnections(3)
//                .workerExecutor(new ThreadPoolExecutor(2, 2, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>()))
                .start();
        TimeUnit.MINUTES.sleep(10);
        proxy.stop();
    }

    @Test
    public void testFTP() throws Exception {
        SimpleReverseTcpProxy proxy = SimpleReverseTcpProxy.create("10.0.0.1", 66);
        proxy.port(33).start();
        TimeUnit.SECONDS.sleep(10);
        proxy.stop();
    }

    @Test
    public void testHTTP() throws Exception {
        SimpleReverseTcpProxy proxy = SimpleReverseTcpProxy.create("10.0.0.1", 4321);
        proxy.port(44)
                .start();
        TimeUnit.HOURS.sleep(10);
        proxy.stop();
    }
}
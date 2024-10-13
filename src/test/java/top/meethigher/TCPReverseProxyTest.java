package top.meethigher;

import org.junit.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TCPReverseProxyTest {


    @Test
    public void testSSH() throws Exception {
        TCPReverseProxy localhost = new TCPReverseProxy("127.0.0.1", 11, "10.0.0.9", 22);
        localhost.start();
        TimeUnit.MINUTES.sleep(1);
        localhost.stop();
    }


    @Test
    public void testPostgreSQL() throws Exception {
        TCPReverseProxy localhost = new TCPReverseProxy("0.0.0.0", 22,
                "10.0.0.9", 5432,
                new ThreadPoolExecutor(50, 50, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>()));
        localhost.start();
        TimeUnit.MINUTES.sleep(1);
        localhost.stop();
    }

    @Test
    public void testFTP() throws Exception {
        TCPReverseProxy localhost = new TCPReverseProxy("127.0.0.1", 33, "127.0.0.1", 66);
        localhost.start();
        TimeUnit.MINUTES.sleep(1);
        localhost.stop();
    }
}
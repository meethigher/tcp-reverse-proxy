package top.meethigher;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class TCPReverseProxyTest {


    @Test
    public void testSSH() throws Exception {
        TCPReverseProxy localhost = new TCPReverseProxy("localhost", 11, "10.0.0.9", 22);
        localhost.start();
        TimeUnit.HOURS.sleep(1);
        localhost.stop();
    }


    @Test
    public void testPostgreSQL() throws Exception {
        TCPReverseProxy localhost = new TCPReverseProxy("localhost", 22, "10.0.0.9", 5432);
        localhost.start();
        TimeUnit.HOURS.sleep(1);
        localhost.stop();
    }

    @Test
    public void testFTP() throws Exception {
        TCPReverseProxy localhost = new TCPReverseProxy("localhost", 33, "localhost", 66);
        localhost.start();
        TimeUnit.HOURS.sleep(1);
        localhost.stop();
    }
}
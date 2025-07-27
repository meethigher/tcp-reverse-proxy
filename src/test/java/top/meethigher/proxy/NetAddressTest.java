package top.meethigher.proxy;

import org.junit.Test;

public class NetAddressTest {
    @Test
    public void name() {
        NetAddress netAddress1 = new NetAddress("127.0.0.1", 6666);
        NetAddress netAddress2 = new NetAddress("127.0.0.1", 6666);
        NetAddress netAddress3 = new NetAddress("127.0.0.1", 6667);
        System.out.println(netAddress2.equals(netAddress1));
        System.out.println(netAddress3.equals(netAddress1));
        System.out.println(netAddress2 == netAddress1);
        System.out.println(netAddress3 == netAddress1);
    }
}
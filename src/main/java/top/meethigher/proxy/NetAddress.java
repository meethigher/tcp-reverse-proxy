package top.meethigher.proxy;

import java.util.Objects;

public class NetAddress {
    private final String host;
    private final int port;

    public NetAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NetAddress that = (NetAddress) o;
        return this.host.equals(that.getHost()) && this.port == that.getPort();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.toString());
    }

    public static NetAddress parse(String addr) {
        try {
            String[] addrArr = addr.split(":");
            return new NetAddress(addrArr[0], Integer.parseInt(addrArr[1]));
        } catch (Exception e) {
            return null;
        }
    }
}

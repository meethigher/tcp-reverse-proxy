package top.meethigher.proxy.tcp.mux.model;

import top.meethigher.proxy.NetAddress;

public class MuxNetAddress extends NetAddress {

    private final String name;

    public MuxNetAddress(String host, int port, String name) {
        super(host, port);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

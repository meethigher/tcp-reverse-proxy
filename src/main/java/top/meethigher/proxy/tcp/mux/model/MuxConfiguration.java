package top.meethigher.proxy.tcp.mux.model;

import top.meethigher.proxy.NetAddress;

public class MuxConfiguration {

    public final String name;
    public final int sessionId;
    public final NetAddress backendServer;


    public MuxConfiguration(String name, int sessionId, NetAddress backendServer) {
        this.name = name;
        this.sessionId = sessionId;
        this.backendServer = backendServer;
    }

    @Override
    public String toString() {
        return name + "," + sessionId + "," + backendServer.toString();
    }

    public static MuxConfiguration parse(String configuration) {
        MuxConfiguration muxConfiguration = null;
        try {
            if (configuration.contains(",")) {
                String[] arr = configuration.split(",");
                muxConfiguration = new MuxConfiguration(arr[0], Integer.parseInt(arr[1]), NetAddress.parse(arr[2]));
            }
        } catch (Exception ignore) {
        }
        return muxConfiguration;
    }
}

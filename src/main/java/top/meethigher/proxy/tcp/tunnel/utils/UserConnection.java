package top.meethigher.proxy.tcp.tunnel.utils;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

import java.util.List;

public class UserConnection {
    public final int sessionId;
    public final NetSocket netSocket;
    public final List<Buffer> buffers;


    public UserConnection(int sessionId, NetSocket netSocket, List<Buffer> buffers) {
        this.sessionId = sessionId;
        this.netSocket = netSocket;
        this.buffers = buffers;
    }
}
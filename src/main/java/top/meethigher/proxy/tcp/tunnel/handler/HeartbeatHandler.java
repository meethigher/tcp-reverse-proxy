package top.meethigher.proxy.tcp.tunnel.handler;

import io.vertx.core.net.NetSocket;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.proto.TunnelMessage;

public class HeartbeatHandler extends TunnelHandler {
    public HeartbeatHandler(NetSocket netSocket, TunnelMessageType type, TunnelMessageType respType, byte[] bodyBytes) {
        super(netSocket, type, respType, bodyBytes);
    }

    @Override
    protected boolean doHandler() {
        TunnelMessage.HeartbeatAck ack = TunnelMessage.HeartbeatAck.newBuilder().setTimestamp(System.currentTimeMillis()).build();
        netSocket.write(TunnelMessageCodec.encode(respType.code(), ack.toByteArray()));
        return true;
    }
}

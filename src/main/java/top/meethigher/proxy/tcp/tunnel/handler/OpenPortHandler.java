package top.meethigher.proxy.tcp.tunnel.handler;

import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.proto.TunnelMessage;

public class OpenPortHandler extends TunnelHandler {
    private static final Logger log = LoggerFactory.getLogger(OpenPortHandler.class);

    public OpenPortHandler(NetSocket netSocket, TunnelMessageType type, TunnelMessageType respType, byte[] bodyBytes) {
        super(netSocket, type, respType, bodyBytes);
    }

    @Override
    protected boolean doHandler() {
        TunnelMessage.OpenPortAck.Builder builder = TunnelMessage.OpenPortAck.newBuilder();
        boolean success = false;
        String msg = "";
        try {
            TunnelMessage.OpenPort parsed = TunnelMessage.OpenPort.parseFrom(bodyBytes);
            int port = parsed.getPort();
            log.info("开启 {} 端口", port);
            success = true;

        } catch (Exception e) {
            msg = e.getMessage();
            log.error("");
        } finally {
            netSocket.write(TunnelMessageCodec.encode(respType.code(), builder.setSuccess(success).setMessage(msg).build().toByteArray()));
        }
        return success;
    }
}

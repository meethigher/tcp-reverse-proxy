package top.meethigher.proxy.tcp.tunnel.handler;

import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageCodec;
import top.meethigher.proxy.tcp.tunnel.codec.TunnelMessageType;
import top.meethigher.proxy.tcp.tunnel.proto.TunnelMessage;

import java.util.concurrent.atomic.AtomicBoolean;

public class AuthHandler extends TunnelHandler {


    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);

    public AuthHandler(NetSocket netSocket, TunnelMessageType type, TunnelMessageType respType, byte[] bodyBytes) {
        super(netSocket, type, respType, bodyBytes);
    }

    @Override
    protected boolean doHandler() {
        TunnelMessage.AuthAck.Builder builder = TunnelMessage.AuthAck.newBuilder();
        AtomicBoolean success = new AtomicBoolean(false);
        String msg = "";
        try {
            TunnelMessage.Auth parsed = TunnelMessage.Auth.parseFrom(bodyBytes);
            String token = parsed.getToken();
            if ("token".equals(token)) {
                success.set(true);
            } else {
                msg = "auth failed";
            }
        } catch (Exception e) {
            msg = e.getMessage();
            log.error("Error parsing message: {}", type, e);
        } finally {

            netSocket.write(TunnelMessageCodec.encode(respType.code(), builder.setSuccess(success.get()).setMessage(msg).build().toByteArray())).onSuccess(v -> {
                if (!success.get()) {
                    netSocket.close();
                }
            });
        }
        return success.get();
    }
}

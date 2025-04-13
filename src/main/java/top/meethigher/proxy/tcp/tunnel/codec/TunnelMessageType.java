package top.meethigher.proxy.tcp.tunnel.codec;

/**
 * 消息类型
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2025/04/04 23:11
 */
public enum TunnelMessageType {

    OPEN_DATA_PORT(0x0001),
    OPEN_DATA_PORT_ACK(0x0002),

    HEARTBEAT(0x0003),
    HEARTBEAT_ACK(0x0004),

    OPEN_DATA_CONN(0x0005),
    OPEN_DATA_CONN_ACK(0x0006),

    ;

    private final int code;

    TunnelMessageType(int code) {
        this.code = code;
    }

    public short code() {
        return (short) code;
    }

    public static TunnelMessageType fromCode(short code) {
        for (TunnelMessageType type : values()) {
            if (type.code() == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + code);
    }
}

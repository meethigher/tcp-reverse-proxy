package top.meethigher.proxy.tcp.mux;

import io.vertx.core.Vertx;

import java.util.concurrent.ThreadLocalRandom;

/**
 * ReverseTcpProxyMuxServer
 * <p>
 * 搭配{@code top.meethigher.proxy.tcp.mux.ReverseTcpProxyMuxClient }实现单端口多路复用
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc1078">RFC 1078 - TCP port service Multiplexer (TCPMUX)</a>
 * @since 2025/07/26 20:41
 */
public class ReverseTcpProxyMuxServer extends Mux {

//    protected final NetServer netServer;
//    protected final NetClient netClient;
//    protected final String name;

    public ReverseTcpProxyMuxServer(Vertx vertx, String secret) {
        super(vertx, secret);
    }

    public static String generateName() {
        final String prefix = ReverseTcpProxyMuxServer.class.getSimpleName() + "-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger(ReverseTcpProxyMuxServer.class.getName() + ".name", 0) + 1);
                System.setProperty(ReverseTcpProxyMuxServer.class.getName() + ".name", next);
                return prefix + next;
            }
        } catch (Exception e) {
            final ThreadLocalRandom random = ThreadLocalRandom.current();
            final StringBuilder sb = new StringBuilder(prefix);
            for (int i = 0; i < 4; i++) {
                sb.append(ID_CHARACTERS[random.nextInt(62)]);
            }
            return sb.toString();
        }
    }

}

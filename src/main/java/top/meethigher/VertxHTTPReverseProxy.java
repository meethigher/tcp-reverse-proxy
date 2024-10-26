//package top.meethigher;
//
//import io.vertx.core.http.HttpClient;
//import io.vertx.core.http.HttpServer;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.concurrent.ThreadLocalRandom;
//
//public class VertxHTTPReverseProxy {
//
//    private static final Logger log = LoggerFactory.getLogger(VertxHTTPReverseProxy.class);
//
//    private static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
//
//    private String sourceHost = "0.0.0.0";
//
//    private int sourcePort = 999;
//
//    private final HttpServer server;
//
//    private final HttpClient httpClient;
//
//    private final HttpClient httpsClient;
//
//    private final String targetHost;
//
//    private final int targetPort;
//
//    private final String name;
//
//    private static String generateName() {
//        final String prefix = "VertxHTTPReverseProxy-";
//        try {
//            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
//            synchronized (System.getProperties()) {
//                final String next = String.valueOf(Integer.getInteger("top.meethigher.VertxHTTPReverseProxy.name", 0) + 1);
//                System.setProperty("top.meethigher.VertxHTTPReverseProxy.name", next);
//                return prefix + next;
//            }
//        } catch (Exception e) {
//            final ThreadLocalRandom random = ThreadLocalRandom.current();
//            final StringBuilder sb = new StringBuilder(prefix);
//            for (int i = 0; i < 4; i++) {
//                sb.append(ID_CHARACTERS[random.nextInt(62)]);
//            }
//            return sb.toString();
//        }
//    }
//}

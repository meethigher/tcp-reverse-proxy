package top.meethigher.proxy.tcp;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简易的Tcp反向代理工具
 * 该工具只做简单使用，不适用于高并发的场景。
 *
 * @author <a href="https://meethigher.top">chenchuancheng</a>
 * @since 2024/10/13 21:34
 */
public class SimpleReverseTcpProxy {

    private static final Logger log = LoggerFactory.getLogger(SimpleReverseTcpProxy.class);

    private static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private final String targetHost;

    private final int targetPort;

    private final ExecutorService bossExecutor;

    private final String name;

    private final Map<Socket, Socket> connections = new HashMap<>();

    /**
     * 反向代理1个tcp连接，使用的是bio模式，需要使用两个线程，直到tcp连接关闭方能释放。
     * 因此建议线程池的 maxPoolSize=maxConnections*2
     */
    private ExecutorService workerExecutor;

    /**
     * 最大连接数
     */
    private int maxConnections;

    private int bufferSize = 2 * 1024;


    private ServerSocket serverSocket;

    private String sourceHost = "0.0.0.0";

    private int sourcePort = 88;

    private SimpleReverseTcpProxy(String targetHost, int targetPort, String name) {
        this.name = name;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.maxConnections = 2;
        this.workerExecutor = new ThreadPoolExecutor(1, maxConnections * 2, 1, TimeUnit.MINUTES, new SynchronousQueue<>(), new ThreadFactory() {
            final AtomicInteger ai = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(name + "-worker-" + ai.getAndIncrement());
                return thread;
            }
        });
        this.bossExecutor = Executors.newFixedThreadPool(1, r -> {
            Thread thread = new Thread(r);
            thread.setName(name + "-boss");
            return thread;
        });
    }

    public static SimpleReverseTcpProxy create(String targetHost, int targetPort) {
        return new SimpleReverseTcpProxy(targetHost, targetPort, generateName());
    }

    public static SimpleReverseTcpProxy create(String targetHost, int targetPort, String name) {
        return new SimpleReverseTcpProxy(targetHost, targetPort, name);
    }

    public SimpleReverseTcpProxy workerExecutor(ExecutorService workerExecutor) {
        this.workerExecutor = workerExecutor;
        return this;
    }

    public SimpleReverseTcpProxy maxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }

    public SimpleReverseTcpProxy bufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
        return this;
    }

    public SimpleReverseTcpProxy host(String host) {
        this.sourceHost = host;
        return this;
    }

    public SimpleReverseTcpProxy port(int port) {
        this.sourcePort = port;
        return this;
    }

    public void start() {
        bossExecutor.submit(() -> {
            try {
                serverSocket = new ServerSocket(sourcePort, 0, InetAddress.getByName(sourceHost));
                log.info("{} started {}:{} <--> {}:{}", name, sourceHost, sourcePort, targetHost, targetPort);
                while (!serverSocket.isClosed()) {
                    try {
                        /**
                         * 将sourceSocket获取的内容，写入到targetSocket
                         * 然后将targetSocket写出的内容，转写给sourceSocket
                         */
                        Socket sourceSocket = serverSocket.accept();
                        if (connections.size() >= maxConnections) {
                            sourceSocket.close();
                            continue;
                        }
                        Socket targetSocket = new Socket(targetHost, targetPort);
                        connections.put(sourceSocket, targetSocket);
                        log.info("{} connected, proxy to {}", sourceSocket.getRemoteSocketAddress().toString(), targetSocket.getRemoteSocketAddress().toString());
                        // 监听源端主动传入的消息写给目标端
                        workerExecutor.execute(() -> {
                            // isClosed只能监听本地连接状态。若远端关闭或者网络问题，无法监听到。
                            if (sourceSocket.isClosed() || targetSocket.isClosed()) {
                                return;
                            }
                            try (InputStream sourceIS = sourceSocket.getInputStream();
                                 OutputStream targetOS = targetSocket.getOutputStream()) {
                                byte[] buffer = new byte[bufferSize];
                                int len;
                                while ((len = sourceIS.read(buffer)) != -1) {
                                    targetOS.write(buffer, 0, len);
                                    log.debug("transferred {} --> {}", sourceSocket, targetSocket);
                                }
                                targetOS.flush();
                            } catch (Exception ignore) {
                            } finally {
                                try {
                                    log.info("closed {} <--> {}", sourceSocket, targetSocket);
                                    sourceSocket.close();
                                    targetSocket.close();
                                    connections.remove(sourceSocket);
                                } catch (Exception ignore) {
                                }
                            }
                        });
                        // 监听目标端主动写回的消息写回源端
                        workerExecutor.execute(() -> {
                            if (sourceSocket.isClosed() || targetSocket.isClosed()) {
                                return;
                            }
                            try (OutputStream sourceOS = sourceSocket.getOutputStream();
                                 InputStream targetIS = targetSocket.getInputStream()) {
                                byte[] buffer = new byte[bufferSize];
                                int len;
                                while ((len = targetIS.read(buffer)) != -1) {
                                    sourceOS.write(buffer, 0, len);
                                    log.debug("transferred {} <-- {}", sourceSocket, targetSocket);
                                }
                                sourceOS.flush();
                            } catch (Exception ignore) {
                            } finally {
                                try {
                                    log.info("closed {} <--> {}", sourceSocket, targetSocket);
                                    sourceSocket.close();
                                    targetSocket.close();
                                    connections.remove(sourceSocket);
                                } catch (Exception ignore) {
                                }
                            }
                        });
                    } catch (Exception e) {
                        log.error("error", e);
                    }
                }
            } catch (Exception e) {
                log.error("{} start error", name, e);
            }
        });
    }


    public void stop() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
                bossExecutor.shutdownNow();
            } catch (Exception ignore) {
            }
            log.info("{} stoped", name);
        }
    }

    private static String generateName() {
        final String prefix = SimpleReverseTcpProxy.class.getSimpleName() + "-";
        try {
            // 池号对于虚拟机来说是全局的，以避免在类加载器范围的环境中池号重叠
            synchronized (System.getProperties()) {
                final String next = String.valueOf(Integer.getInteger(SimpleReverseTcpProxy.class.getName() + ".name", 0) + 1);
                System.setProperty(SimpleReverseTcpProxy.class.getName() + ".name", next);
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

package top.meethigher;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPReverseProxy {

    private static final Logger log = LoggerFactory.getLogger(TCPReverseProxy.class);

    private final String sourceHost;

    private final int sourcePort;

    private final String targetHost;

    private final int targetPort;

    private final int bufferSize = 2 * 1024;

    private ServerSocket serverSocket;

    private final ExecutorService executor;

    public TCPReverseProxy(String sourceHost, int sourcePort, String targetHost, int targetPort) {
        this.sourceHost = sourceHost;
        this.sourcePort = sourcePort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        executor = Executors.newFixedThreadPool(1);
    }

    public void start() {
        executor.submit(() -> {
            try {
                serverSocket = new ServerSocket(sourcePort, 0, InetAddress.getByName(sourceHost));
                log.info("proxy server started {}:{} <--> {}:{}", sourceHost, sourcePort, targetHost, targetPort);
                while (!serverSocket.isClosed()) {
                    Socket sourceSocket = serverSocket.accept();
                    CompletableFuture.runAsync(() -> {
                        try {
                            /**
                             * 将sourceSocket获取的内容，写入到targetSocket
                             * 然后将targetSocket写出的内容，转写给sourceSocket
                             */
                            Socket targetSocket = new Socket(targetHost, targetPort);
                            log.info("{} connected, proxy to {}", sourceSocket.getRemoteSocketAddress().toString(), targetSocket.getRemoteSocketAddress().toString());
                            // 监听源端主动传入的消息写给目标端
                            CompletableFuture.runAsync(() -> {
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
                                        log.debug("{} --> {}", sourceSocket, targetSocket);
                                    }
                                    targetOS.flush();
                                } catch (Exception ignore) {
                                } finally {
                                    try {
                                        log.info("{} <--> {} closed", sourceSocket, targetSocket);
                                        sourceSocket.close();
                                        targetSocket.close();
                                    } catch (Exception ignore) {
                                    }
                                }
                            });
                            // 监听目标端主动写回的消息写回源端
                            CompletableFuture.runAsync(() -> {
                                if (sourceSocket.isClosed() || targetSocket.isClosed()) {
                                    return;
                                }
                                try (OutputStream sourceOS = sourceSocket.getOutputStream();
                                     InputStream targetIS = targetSocket.getInputStream()) {
                                    byte[] buffer = new byte[bufferSize];
                                    int len;
                                    while ((len = targetIS.read(buffer)) != -1) {
                                        sourceOS.write(buffer, 0, len);
                                        log.debug("{} <-- {}", sourceSocket, targetSocket);
                                    }
                                    sourceOS.flush();
                                } catch (Exception ignore) {
                                } finally {
                                    try {
                                        log.info("{} <--> {} closed", sourceSocket, targetSocket);
                                        sourceSocket.close();
                                        targetSocket.close();
                                    } catch (Exception ignore) {
                                    }
                                }
                            });
                        } catch (Exception e) {
                            log.error("error", e);
                        }
                    });
                }
            } catch (Exception e) {
                log.error("ProxyServerSocket start error", e);
            }
        });
    }


    public void stop() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignore) {
            }
        }
        executor.shutdown();
        log.info("proxy server stoped {}:{} <--> {}:{}", sourceHost, sourcePort, targetHost, targetPort);
    }

}

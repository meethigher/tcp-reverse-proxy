package top.meethigher.proxy.tcp.tunnel.issue8;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;

public class Issue8BackendServer {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        NetServer netServer = vertx.createNetServer();
        netServer.connectHandler(socket -> {
            socket.remoteAddress();
            socket.write(Buffer.buffer("SSH-2.0-OpenSSH_8.7")).onComplete(ar -> {
                System.out.println(socket.remoteAddress().toString() + " write " + ar.succeeded());
            });
        }).listen(23).onComplete(ar -> {
            if (ar.succeeded()) {
                System.out.println("Server started on port " + ar.result().actualPort());
            } else {
                System.err.println("Server failed to start");
                ar.cause().printStackTrace();
                System.exit(1);
            }
        });
    }
}
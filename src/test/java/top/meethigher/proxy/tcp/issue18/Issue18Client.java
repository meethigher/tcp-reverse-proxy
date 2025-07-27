package top.meethigher.proxy.tcp.issue18;

import io.vertx.core.Vertx;
import io.vertx.core.http.RequestOptions;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class Issue18Client {
    public static void main(String[] args) throws Exception {
        TimeUnit.SECONDS.sleep(2);
        Vertx vertx = Vertx.vertx();
        // tcp比较底层
//        vertx.createNetClient().connect(8080, "127.0.0.1")
//                .onSuccess(s -> {
//                    // 模拟rst异常断开
//                    System.exit(1);
//                });

        // http比较顶层
        vertx.createHttpClient().request(new RequestOptions().setAbsoluteURI("http://127.0.0.1:8080/api"))
                .onSuccess(res -> {
                    System.exit(1);// http连接建立之前的异常
//                    res.send().onSuccess(vv -> {
//                        System.exit(1);
//                    });
                });

        LockSupport.park();
    }
}

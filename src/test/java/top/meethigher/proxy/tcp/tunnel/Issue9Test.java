package top.meethigher.proxy.tcp.tunnel;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.PoolOptions;
import io.vertx.core.http.RequestOptions;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Issue9Test {
    private static final Logger log = LoggerFactory.getLogger(Issue9Test.class);

    @Test
    public void test() throws Exception {

        /**
         * IDEA 多配置“批量启动”——使用 Run/Debug Configuration 的“Compound”功能
         * 步骤如下：
         * 打开Run/Debug Configurations窗口（快捷键 Ctrl+Alt+R / 右上角 Edit Configurations）。
         *
         * 新建三个 Application 类型配置，分别设置好 ClassA、ClassB、ClassC 的 main。
         *
         * 再新建一个Compound类型配置。
         *
         * 在 Compound 配置的“Run/Debug Configurations”里，添加刚刚那三个配置，顺序拖拽即可调整。
         *
         * 选中 Compound 配置，点绿色启动按钮，一次性批量顺序启动三（或更多）个实例。
         *
         * 注意：Compound 是并发同时，不是顺序启动。IDEA 会按照你添加的顺序一个一个起。
         */

        // step1: run top.meethigher.proxy.tcp.tunnel.issue9.Issue9SimpleHttpServer.main

        // step2: run top.meethigher.proxy.tcp.tunnel.issue9.Issue9TunnelServer.main

        // step3: run top.meethigher.proxy.tcp.tunnel.issue9.Issue9TunnelClient.main

        Vertx vertx = Vertx.vertx();
        HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions()
                        .setKeepAlive(true)
                        .setMaxPoolSize(Runtime.getRuntime().availableProcessors()),
                new PoolOptions().setHttp1MaxSize(100));
        int total = 500;
        CountDownLatch latch = new CountDownLatch(total);
        for (int i = 0; i < total; i++) {
            final String id = String.valueOf(i + 1);
            RequestOptions requestOptions = new RequestOptions()
                    .setAbsoluteURI("http://127.0.0.1:808/api");
            httpClient.request(requestOptions).onSuccess(req -> {
                req.send().onSuccess(resp -> {
                    log.info("{} succeeded", id);
                    latch.countDown();
                });
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        Assert.assertEquals(total, total - latch.getCount());

    }
}

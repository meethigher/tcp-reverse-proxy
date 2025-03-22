package top.meethigher.proxy.http;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class DnsResolverTest {

    public static void main(String[] args) throws Exception {
        String hostname = "reqres.in";
        int iterations = 10;

        System.out.println("Testing with InetAddress...");
        // 当前没有该host缓存时，会直接sun.net.spi.nameservice.NameService.lookupAllHostAddr
        testWithInetAddress(hostname, iterations);

        System.out.println("Testing with Vert.x DnsClient...");
        // io.vertx.core.dns.impl.DnsClientImpl.Query.run
        testWithVertx(hostname, iterations);


    }

    private static void testWithVertx(String hostname, int iterations) throws InterruptedException {
        Vertx vertx = Vertx.vertx(new VertxOptions()
                // .setAddressResolverOptions(new AddressResolverOptions()
                //         .setCacheMinTimeToLive(60)  // DNS 缓存时间，减少重复解析
                //         .setCacheMaxTimeToLive(600)
                //         .setQueryTimeout(5000))

        );

        DnsClient dnsClient = vertx.createDnsClient(
                new DnsClientOptions()
                        .setQueryTimeout(10000)
        );

        CountDownLatch latch = new CountDownLatch(iterations);
        AtomicLong totalTime = new AtomicLong();

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            dnsClient.lookup(hostname, result -> {
                long duration = System.nanoTime() - start;
                if (result.succeeded()) {
                    System.out.println("Vert.x resolved: " + result.result() + " in " + duration / 1_000_000.0 + " ms");
                } else {
                    System.err.println("Vert.x failed to resolve: " + result.cause());
                }
                totalTime.addAndGet(duration);
                latch.countDown();
            });
        }

        latch.await();
        System.out.println("Vert.x Average Time: " + (totalTime.get() / iterations) / 1_000_000.0 + " ms");
        vertx.close();
    }

    private static void testWithInetAddress(String hostname, int iterations) throws Exception {
        long totalTime = 0;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            InetAddress address = InetAddress.getByName(hostname);
            long duration = System.nanoTime() - start;
            System.out.println("InetAddress resolved: " + address.getHostAddress() + " in " + duration / 1_000_000.0 + " ms");
            totalTime += duration;
        }

        System.out.println("InetAddress Average Time: " + (totalTime / iterations) / 1_000_000.0 + " ms");
    }
}
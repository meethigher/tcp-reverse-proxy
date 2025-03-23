package top.meethigher.proxy.http;

import org.junit.Test;

public class UrlParserTest {


    @Test
    public void fastReplaceAgg() {

        String origin = "http://10.0.0.1:8080";
        String target = "https://meethigher.top";

        // 函数进行预热
        int preheatNumber = 2000;

        int total = 5000000;

        // 预热
        for (int i = 0; i < 2; i++) {
            if (i == 0) {
                for (int j = 0; j < preheatNumber; j++) {
                    String replace = origin.replace(origin, target);
                }
            } else {
                for (int j = 0; j < preheatNumber; j++) {
                    String replace = UrlParser.fastReplace(origin, origin, target);
                }
            }
        }

        // 实际耗时记录
        for (int i = 0; i < 2; i++) {
            long start = System.currentTimeMillis();
            if (i == 0) {
                for (int j = 0; j < total; j++) {
                    String replace = origin.replace(origin, target);
                }
            } else {
                for (int j = 0; j < total; j++) {
                    String replace = UrlParser.fastReplace(origin, origin, target);
                }
            }
            long end = System.currentTimeMillis();
            System.out.println("方案 " + i + " 耗时: " + (end - start) + " ms");
        }

    }


    @Test
    public void fastReplace1() {

        String origin = "http://10.0.0.1:8080";
        String target = "https://meethigher.top";

        // 函数进行预热
        int preheatNumber = 2000;

        int total = 5000000;
        for (int i = 0; i < preheatNumber; i++) {
            String replace = origin.replace(origin, target);
        }

        long start = System.currentTimeMillis();
        for (int j = 0; j < total; j++) {
            String replace = origin.replace(origin, target);
        }
        long end = System.currentTimeMillis();
        System.out.println("耗时: " + (end - start) + " ms");

    }


    @Test
    public void fastReplace2() {

        String origin = "http://10.0.0.1:8080";
        String target = "https://meethigher.top";

        // 函数进行预热
        int preheatNumber = 2000;

        int total = 5000000;
        for (int i = 0; i < preheatNumber; i++) {
            String replace = UrlParser.fastReplace(origin, origin, target);
        }

        long start = System.currentTimeMillis();
        for (int j = 0; j < total; j++) {
            String replace = UrlParser.fastReplace(origin, origin, target);
        }
        long end = System.currentTimeMillis();
        System.out.println("耗时: " + (end - start) + " ms");

    }


    @Test
    public void fastReplace() {
        String str = "hello, world";
        System.out.println(UrlParser.fastReplace(str, ",", "!"));
    }
}
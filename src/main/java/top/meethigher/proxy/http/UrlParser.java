package top.meethigher.proxy.http;

import java.net.URI;

public class UrlParser {


    /**
     * 在压测时，性能比String.replace略优
     * @param text 需要替换的文本
     * @param search 要搜索的字符串
     * @param replacement 替换的字符串
     * @return 替换后的文本
     */
    public static String fastReplace(String text, String search, String replacement) {
        if (text == null || search == null || replacement == null || search.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        int start = 0, index;
        while ((index = text.indexOf(search, start)) >= 0) {
            result.append(text, start, index).append(replacement);
            start = index + search.length();
        }
        result.append(text.substring(start));
        return result.toString();
    }


    /**
     * 拼接两个uri。不要求uri以"/"结尾
     * @param uri1 第一个URI字符串
     * @param uri2 第二个URI字符串
     * @return 拼接后的URI字符串
     */
    public static String joinURI(String uri1, String uri2) {
        if (uri1.endsWith("/") && uri2.startsWith("/")) {
            // 两边都有 '/'
            return uri1 + uri2.substring(1);
        } else if (!uri1.endsWith("/") && !uri2.startsWith("/")) {
            // 两边都没有 '/'
            return uri1 + "/" + uri2;
        } else {
            // 只有一个有 '/'
            return uri1 + uri2;
        }
    }

    public static ParsedUrl parseUrl(String url) {
        try {
            URI uri = new URI(url);
            boolean isSsl = "https".equalsIgnoreCase(uri.getScheme());
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : (isSsl ? 443 : 80);
            String path = uri.getRawPath();
            String query = uri.getRawQuery();

            return new ParsedUrl(isSsl, host, port, path, query);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }
    }

    public static String getUrl(ParsedUrl parsedUrl) {
        return parsedUrl.getFormatUrl();
    }

    public static class ParsedUrl {
        public final boolean isSsl;
        public final String host;
        public final int port;
        public final String uri;
        public final String query;

        public ParsedUrl(boolean isSsl, String host, int port, String uri,
                         String query) {
            this.isSsl = isSsl;
            this.host = host;
            this.port = port;
            this.uri = uri.replaceAll("/+$", "");
            this.query = query;
        }


        public String getFormatUrl() {
            return (isSsl ? "https" : "http") + "://" + host + ":" + port + uri + (query == null ? "" : ("?" + query));
        }

        public String getFormatUri() {
            return (isSsl ? "https" : "http") + "://" + host + ":" + port + uri;
        }

        public String getFormatHostPort() {
            return (isSsl ? "https" : "http") + "://" + host + ":" + port;
        }
    }
}

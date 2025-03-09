package top.meethigher.proxy.http;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务信息
 *
 * @author chenchuancheng
 * @since 2024/07/17 23:21
 */
public class ProxyRoute implements Serializable {

    private String name;

    /**
     * /* represents all interfaces below proxy /, no distinction between /* and /**
     */
    private String sourceUrl;

    private String targetUrl;

    private boolean forwardIp = false;

    private boolean preserveCookies = true;

    private boolean preserveHost = false;

    private boolean followRedirects = true;

    private boolean httpKeepAlive = true;

    private Log log = new Log();

    private CorsControl corsControl = new CorsControl();


    public String getSourceUrl() {
        return sourceUrl;
    }

    public ProxyRoute setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
        return this;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public ProxyRoute setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
        return this;
    }

    public Log getLog() {
        return log;
    }

    public boolean isHttpKeepAlive() {
        return httpKeepAlive;
    }

    public ProxyRoute setHttpKeepAlive(boolean httpKeepAlive) {
        this.httpKeepAlive = httpKeepAlive;
        return this;
    }

    public ProxyRoute setLog(Log log) {
        this.log = log;
        return this;
    }

    public CorsControl getCorsControl() {
        return corsControl;
    }

    public ProxyRoute setCorsControl(CorsControl corsControl) {
        this.corsControl = corsControl;
        return this;
    }

    public String getName() {
        return name;
    }

    public ProxyRoute setName(String name) {
        this.name = name;
        return this;
    }

    public boolean isForwardIp() {
        return forwardIp;
    }

    public ProxyRoute setForwardIp(boolean forwardIp) {
        this.forwardIp = forwardIp;
        return this;
    }

    public boolean isPreserveCookies() {
        return preserveCookies;
    }

    public ProxyRoute setPreserveCookies(boolean preserveCookies) {
        this.preserveCookies = preserveCookies;
        return this;
    }

    public boolean isPreserveHost() {
        return preserveHost;
    }

    public ProxyRoute setPreserveHost(boolean preserveHost) {
        this.preserveHost = preserveHost;
        return this;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public ProxyRoute setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("name", getName());
        map.put("sourceUrl", getSourceUrl());
        map.put("targetUrl", getTargetUrl());
        map.put("forwardIp", String.valueOf(isForwardIp()));
        map.put("preserveHost", String.valueOf(isPreserveHost()));
        map.put("preserveCookies", String.valueOf(isPreserveCookies()));
        map.put("followRedirects", String.valueOf(isFollowRedirects()));
        map.put("httpKeepAlive", String.valueOf(isHttpKeepAlive()));
        map.put("log.enable", String.valueOf(getLog().isEnable()));
        map.put("log.logFormat", String.valueOf(getLog().getLogFormat()));
        map.put("corsControl.enable", String.valueOf(getCorsControl().isEnable()));
        map.put("corsControl.allowCors", String.valueOf(getCorsControl().isAllowCors()));
        return map;
    }


    /**
     * 跨域控制
     *
     * @author chenchuancheng
     * @since 2024/06/29 11:59
     */
    public static class CorsControl {
        /**
         * true表示所有的代理请求的跨域都由自己管理
         * false表示所有的代理请求的跨域由被代理方控制
         */
        private boolean enable = false;

        /**
         * 当enable=true时，该参数才会生效
         * 如果该参数为true表示所有经过代理的服务都允许跨域
         * 如果该参数为true表示所有经过代理的服务均不允许跨域
         */
        private boolean allowCors = false;

        public boolean isEnable() {
            return enable;
        }

        public CorsControl setEnable(boolean enable) {
            this.enable = enable;
            return this;
        }

        public boolean isAllowCors() {
            return allowCors;
        }

        public CorsControl setAllowCors(boolean allowCors) {
            this.allowCors = allowCors;
            return this;
        }
    }

    public static class Log {
        private boolean enable = true;
        /**
         * Configure the agent’s log format. The options are remoteAddr、remotePort、userAgent、method、source、target
         */
        private String logFormat = ReverseHttpProxy.LOG_FORMAT_DEFAULT;

        public boolean isEnable() {
            return enable;
        }

        public Log setEnable(boolean enable) {
            this.enable = enable;
            return this;
        }

        public String getLogFormat() {
            return logFormat;
        }

        public Log setLogFormat(String logFormat) {
            this.logFormat = logFormat;
            return this;
        }
    }
}
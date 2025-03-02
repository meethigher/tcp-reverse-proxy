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

    private LOG log = new LOG();

    private CORSControl corsControl = new CORSControl();


    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public LOG getLog() {
        return log;
    }

    public boolean isHttpKeepAlive() {
        return httpKeepAlive;
    }

    public void setHttpKeepAlive(boolean httpKeepAlive) {
        this.httpKeepAlive = httpKeepAlive;
    }

    public void setLog(LOG log) {
        this.log = log;
    }

    public CORSControl getCorsControl() {
        return corsControl;
    }

    public void setCorsControl(CORSControl corsControl) {
        this.corsControl = corsControl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isForwardIp() {
        return forwardIp;
    }

    public void setForwardIp(boolean forwardIp) {
        this.forwardIp = forwardIp;
    }

    public boolean isPreserveCookies() {
        return preserveCookies;
    }

    public void setPreserveCookies(boolean preserveCookies) {
        this.preserveCookies = preserveCookies;
    }

    public boolean isPreserveHost() {
        return preserveHost;
    }

    public void setPreserveHost(boolean preserveHost) {
        this.preserveHost = preserveHost;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
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
        map.put("corsControl.allowCors", String.valueOf(getCorsControl().isAllowCORS()));
        return map;
    }


    /**
     * 跨域控制
     *
     * @author chenchuancheng
     * @since 2024/06/29 11:59
     */
    public static class CORSControl {
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
        private boolean allowCORS;

        public boolean isEnable() {
            return enable;
        }

        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        public boolean isAllowCORS() {
            return allowCORS;
        }

        public void setAllowCORS(boolean allowCORS) {
            this.allowCORS = allowCORS;
        }
    }

    public static class LOG {
        private boolean enable = true;
        /**
         * Configure the agent’s log format. The options are remoteAddr、remotePort、userAgent、method、source、target
         */
        private String logFormat = "{name} -- {method} -- {userAgent} -- {remoteAddr}:{remotePort} -- {source} --> {target} -- {statusCode} consumed {consumedMills} ms";

        public boolean isEnable() {
            return enable;
        }

        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        public String getLogFormat() {
            return logFormat;
        }

        public void setLogFormat(String logFormat) {
            this.logFormat = logFormat;
        }
    }
}
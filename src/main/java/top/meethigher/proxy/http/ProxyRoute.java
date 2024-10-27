package top.meethigher.proxy.http;

/**
 * 服务信息
 *
 * @author chenchuancheng
 * @since 2024/07/17 23:21
 */
public class ProxyRoute {

    private boolean enable;

    private String name;
    /**
     * /* represents all interfaces below proxy /, no distinction between /* and /**
     */
    private String sourceUrl;

    private String targetUrl;

    private boolean forwardIp;

    private boolean preserveCookies;

    private boolean preserveHost;

    private boolean followRedirects;

    private LOG log;

    private CORSControl corsControl;


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
        private boolean enable;

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
        private boolean enable;
        /**
         * Configure the agent’s log format. The options are remoteAddr、remotePort、userAgent、method、source、target
         */
        private String logFormat;

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


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

    public LOG getLog() {
        return log;
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
}
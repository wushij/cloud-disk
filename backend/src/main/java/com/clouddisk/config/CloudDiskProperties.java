package com.clouddisk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "clouddisk")
public class CloudDiskProperties {
    private Redis redis = new Redis();
    private Storage storage = new Storage();
    private Minio minio = new Minio();
    private Chunk chunk = new Chunk();
    private Upload upload = new Upload();
    private OnlyOffice onlyoffice = new OnlyOffice();
    private Schedule schedule = new Schedule();
    private RateLimit rateLimit = new RateLimit();
    private Sentinel sentinel = new Sentinel();
    private Cors cors = new Cors();
    private Rabbitmq rabbitmq = new Rabbitmq();
    private Elasticsearch elasticsearch = new Elasticsearch();
    private Ffmpeg ffmpeg = new Ffmpeg();
    private Ldap ldap = new Ldap();
    private Sso sso = new Sso();
    private Monitoring monitoring = new Monitoring();
    private Cdn cdn = new Cdn();
    private VirusScan virusScan = new VirusScan();

    @Data
    public static class Redis {
        private boolean enabled = false;
    }

    @Data
    public static class Storage {
        private String type = "local";
        private String localRoot;
    }

    @Data
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
    }

    @Data
    public static class Chunk {
        private int defaultSize = 8 * 1024 * 1024;
        private int maxSize = 64 * 1024 * 1024;
        private int maxChunks = 5000;
        private int sessionExpireHours = 24;
    }

    @Data
    public static class Upload {
        private long maxFileSize = 20L * 1024 * 1024 * 1024;
        /** 设为 * 表示允许除 blocked-extensions 外的所有扩展名 */
        private String allowedExtensions = "*";
        private String blockedExtensions = "exe,bat,cmd,com,scr,vbs,msi,dll,apk,jar,ps1,msh,reg,cpl,hta,inf";
    }

    @Data
    public static class OnlyOffice {
        private boolean enabled = false;
        private String documentServerUrl = "http://127.0.0.1:8082";
        private String jwtSecret = "clouddisk-onlyoffice-jwt-secret-change-me";
        /** 后端对外地址，供 Document Server 回调/拉取文件 */
        private String callbackBaseUrl = "http://127.0.0.1:8088";
        /** edit 或 view */
        private String editMode = "edit";
    }

    @Data
    public static class Schedule {
        private boolean enabled = true;
        private int recycleRetainDays = 30;
    }

    @Data
    public static class RateLimit {
        /** 是否启用 API/上传/IP 滑动窗口限流（local 开发可关） */
        private boolean enabled = true;
        private int uploadPerMinute = 60;
        private int loginPerMinute = 20;
        private int registerPerMinute = 10;
        private int sharePerMinute = 120;
        private int apiPerMinute = 300;
        private int shareExtractMaxAttempts = 5;
        /** 单账号连续登录失败锁定阈值 */
        private int loginFailMax = 5;
        /** 账号/IP 登录失败锁定时长（分钟） */
        private int loginLockMinutes = 15;
        /** 单 IP 累计失败达到后封禁 */
        private int ipBanThreshold = 30;
        /** IP 封禁时长（分钟） */
        private int ipBanMinutes = 60;
        /** IP 失败达到此次数后要求验证码 */
        private int captchaAfterFailures = 3;
        /** 注册是否强制验证码 */
        private boolean captchaOnRegister = true;
    }

    @Data
    public static class Sentinel {
        private boolean enabled = true;
        private String dashboard = "127.0.0.1:8858";
        private int uploadQps = 30;
        private int loginQps = 10;
        private int registerQps = 5;
    }

    @Data
    public static class Cors {
        private List<String> allowedOriginPatterns = List.of("http://localhost:*");
    }

    @Data
    public static class Rabbitmq {
        private boolean enabled = false;
    }

    @Data
    public static class Elasticsearch {
        private boolean enabled = false;
        private String indexName = "cloud_disk_file";
    }

    @Data
    public static class Ffmpeg {
        private boolean enabled = true;
        private String path = "ffmpeg";
        private String screenshotTime = "00:00:01";
        private String videoCodec = "libx264";
        private String audioCodec = "aac";
        private String transcodeSuffix = "_720p.mp4";
    }

    @Data
    public static class Ldap {
        private boolean enabled = false;
        private String urls = "ldap://127.0.0.1:389";
        private String base = "dc=example,dc=com";
        private String userDn = "cn=admin,dc=example,dc=com";
        private String password = "";
        /** 用户搜索，{0} = username */
        private String userSearchFilter = "(&(objectClass=inetOrgPerson)(uid={0}))";
        private String userSearchBase = "ou=users";
        /** 首次 LDAP 登录是否自动创建本地账号 */
        private boolean autoProvision = true;
    }

    @Data
    public static class Sso {
        private boolean enabled = false;
        private String providerName = "OIDC";
        /** 授权端点 */
        private String authorizationUri = "https://login.example.com/oauth2/v1/authorize";
        private String tokenUri = "https://login.example.com/oauth2/v1/token";
        private String userInfoUri = "https://login.example.com/oauth2/v1/userinfo";
        private String clientId = "";
        private String clientSecret = "";
        /** 后端回调地址，需在 IdP 注册 */
        private String redirectUri = "http://127.0.0.1:8088/api/auth/sso/callback";
        /** SSO 成功后跳转前端 */
        private String frontendRedirect = "http://127.0.0.1:5173/login";
        private String scope = "openid profile email";
    }

    @Data
    public static class Monitoring {
        private boolean adminClientEnabled = false;
        private String adminServerUrl = "http://127.0.0.1:8090";
    }

    @Data
    public static class Cdn {
        private boolean enabled = false;
        /** CDN 域名，例如 https://cdn.example.com */
        private String domain = "";
    }

    @Data
    public static class VirusScan {
        private boolean enabled = false;
        private String host = "127.0.0.1";
        private int port = 3310;
        private int timeoutMs = 30000;
        private long maxBytes = 104857600;
    }
}

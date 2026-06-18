package com.clouddisk.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class StartupSuccessListener {

    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("server.port", "8088");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        if (!StringUtils.hasText(contextPath)) {
            contextPath = "";
        } else if (!contextPath.startsWith("/")) {
            contextPath = "/" + contextPath;
        }
        String baseUrl = "http://127.0.0.1:" + port + contextPath;

        String[] profiles = env.getActiveProfiles();
        String profileText = profiles.length > 0 ? String.join(", ", profiles) : "default";

        boolean redisEnabled = env.getProperty("clouddisk.redis.enabled", Boolean.class, false);
        String storageType = env.getProperty("clouddisk.storage.type", "local");

        log.info("");
        log.info("================================================================");
        log.info("  CloudDisk Pro 启动成功");
        log.info("  Profile   : {}", profileText);
        log.info("  存储      : {} | Redis: {}", storageType, redisEnabled ? "已启用" : "未启用");
        log.info("  API       : {}", baseUrl);
        log.info("  接口文档  : {}/doc.html", baseUrl);
        log.info("  健康检查  : {}/actuator/health", baseUrl);
        log.info("  默认账号  : admin / admin123");
        log.info("================================================================");
        log.info("");
    }
}

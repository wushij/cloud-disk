package com.clouddisk.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class StartupSuccessListener {

    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("server.port", "8055");
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

        String c = "\033[36m";  // 青色
        String g = "\033[32m";  // 绿色
        String y = "\033[33m";  // 黄色
        String b = "\033[34m";  // 蓝色
        String r = "\033[0m";   // 重置

        String line = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
        System.out.println();
        System.out.println(c + line + r);
        System.out.println(g + "  CloudDisk Pro 启动成功" + r);
        System.out.println(c + "  ─────────────────────────────────────────────" + r);
        System.out.println("  " + b + "Profile" + r + "    : " + y + profileText + r);
        System.out.println("  " + b + "存储" + r + "       : " + y + storageType + r + " | Redis: " + (redisEnabled ? g + "已启用" + r : r + "未启用"));
        System.out.println("  " + b + "API" + r + "        : " + y + baseUrl + r);
        System.out.println("  " + b + "接口文档" + r + "   : " + y + baseUrl + "/doc.html" + r);
        System.out.println("  " + b + "健康检查" + r + "   : " + y + baseUrl + "/actuator/health" + r);
        System.out.println("  " + b + "默认账号" + r + "   : " + y + "admin" + r + "（首次登录请修改密码）");
        System.out.println(c + line + r);
        System.out.println();
    }
}

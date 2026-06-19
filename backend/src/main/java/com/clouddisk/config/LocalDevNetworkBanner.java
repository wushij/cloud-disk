package com.clouddisk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 本地开发启动时在控制台输出局域网访问地址，便于手机等同网段设备联调。
 */
@Component
@Profile("local")
public class LocalDevNetworkBanner {

    private static final Logger log = LoggerFactory.getLogger(LocalDevNetworkBanner.class);

    private final Environment environment;

    public LocalDevNetworkBanner(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void printLanUrls() {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8088"));
        List<String> ips = collectLanIpv4();
        if (ips.isEmpty()) {
            return;
        }
        log.info("");
        log.info("CloudDisk API - LAN access (same WiFi)");
        for (String ip : ips) {
            log.info("  -> http://{}:{}/", ip, port);
        }
        log.info("  API docs: http://{}:{}/doc.html", ips.get(0), port);
        log.info("");
    }

    private static List<String> collectLanIpv4() {
        List<String> ips = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address inet4 && !inet4.isLoopbackAddress() && !inet4.isLinkLocalAddress()) {
                        ips.add(inet4.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
            // dev-only helper
        }
        return ips.stream().distinct().toList();
    }
}

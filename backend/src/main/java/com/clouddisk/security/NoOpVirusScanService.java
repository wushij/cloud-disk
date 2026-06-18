package com.clouddisk.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "clouddisk.virus-scan", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpVirusScanService implements VirusScanService {

    @Override
    public void scan(InputStream inputStream, String fileName, long fileSize) {
        log.trace("病毒扫描跳过（未配置引擎）: {}", fileName);
    }
}

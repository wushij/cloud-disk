package com.clouddisk.security;

import java.io.InputStream;

/**
 * 病毒扫描扩展接口（设计文档预留）。
 * 默认实现 {@link NoOpVirusScanService} 直接放行；
 * 接入 ClamAV 等引擎时实现本接口并注册为 Bean 即可。
 */
public interface VirusScanService {

    void scan(InputStream inputStream, String fileName, long fileSize);
}

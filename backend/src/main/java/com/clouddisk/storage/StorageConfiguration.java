package com.clouddisk.storage;

import com.clouddisk.config.CloudDiskProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "clouddisk.storage.type", havingValue = "local", matchIfMissing = true)
    public StorageService localStorageService(CloudDiskProperties properties) {
        return new LocalStorageService(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "clouddisk.storage.type", havingValue = "minio")
    public StorageService minioStorageService(CloudDiskProperties properties) {
        return new MinioStorageService(properties);
    }
}

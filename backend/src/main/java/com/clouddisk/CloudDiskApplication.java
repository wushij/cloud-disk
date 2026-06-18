package com.clouddisk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.clouddisk.mapper")
@EnableScheduling
@EnableAsync
public class CloudDiskApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudDiskApplication.class, args);
    }
}

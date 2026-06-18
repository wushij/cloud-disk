package com.clouddisk.search;

import com.clouddisk.config.CloudDiskProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@ConditionalOnProperty(prefix = "clouddisk.elasticsearch", name = "enabled", havingValue = "true")
@EnableElasticsearchRepositories(basePackages = "com.clouddisk.search")
public class ElasticsearchConfig {

    /**
     * 动态索引名称 Bean，供 @Document(indexName = "#{@esIndexName}") 解析
     */
    @Bean("esIndexName")
    public String esIndexName(CloudDiskProperties properties) {
        return properties.getElasticsearch().getIndexName();
    }
}

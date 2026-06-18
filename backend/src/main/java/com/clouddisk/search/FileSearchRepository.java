package com.clouddisk.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

@ConditionalOnProperty(prefix = "clouddisk.elasticsearch", name = "enabled", havingValue = "true")
public interface FileSearchRepository extends ElasticsearchRepository<FileSearchDocument, String> {
}

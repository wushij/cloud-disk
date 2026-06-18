package com.clouddisk.search;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import com.clouddisk.entity.FileRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件搜索服务 —— 基于 ElasticSearch
 * <ul>
 *   <li>indexFile: 文件上传/重命名/移动时同步到 ES</li>
 *   <li>removeFile: 文件删除时从 ES 移除</li>
 *   <li>search: 全文搜索 + 拼音搜索 + 高亮显示</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "clouddisk.elasticsearch", name = "enabled", havingValue = "true")
public class FileSearchService {

    private final FileSearchRepository fileSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    // ====================== 索引操作 ======================

    /**
     * 将文件记录同步到 ES 索引
     */
    public void indexFile(FileRecord file) {
        if (file == null) return;
        try {
            FileSearchDocument doc = toDocument(file);
            fileSearchRepository.save(doc);
            log.debug("ES 索引更新: fileId={}", file.getId());
        } catch (Exception e) {
            log.warn("ES 索引更新失败 fileId={}: {}", file.getId(), e.getMessage());
        }
    }

    /**
     * 批量索引文件
     */
    public void indexFiles(List<FileRecord> files) {
        if (files == null || files.isEmpty()) return;
        try {
            List<FileSearchDocument> docs = files.stream().map(this::toDocument).collect(Collectors.toList());
            fileSearchRepository.saveAll(docs);
            log.debug("ES 批量索引: count={}", docs.size());
        } catch (Exception e) {
            log.warn("ES 批量索引失败: {}", e.getMessage());
        }
    }

    /**
     * 从 ES 移除文件
     */
    public void removeFile(Long fileId) {
        if (fileId == null) return;
        try {
            fileSearchRepository.deleteById(String.valueOf(fileId));
            log.debug("ES 索引删除: fileId={}", fileId);
        } catch (Exception e) {
            log.warn("ES 索引删除失败 fileId={}: {}", fileId, e.getMessage());
        }
    }

    // ====================== 搜索操作 ======================

    /**
     * 搜索文件
     *
     * @param userId   当前用户 ID（必须，仅搜索自己的文件）
     * @param keyword  搜索关键词（支持中文全文 + 拼音）
     * @param fileType 文件类型过滤（image/video/document/archive，可为空）
     * @param page     页码（从 0 开始）
     * @param size     每页大小
     * @return 搜索结果 Map，格式与 FileService.list() 一致
     */
    public Map<String, Object> search(Long userId, String keyword, String fileType, int page, int size) {
        // 构建 Bool 查询
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // 必须匹配当前用户 + 正常状态文件
        boolBuilder.filter(f -> f.term(t -> t.field("userId").value(userId)));
        boolBuilder.filter(f -> f.term(t -> t.field("status").value(1)));

        // 关键词搜索（多字段匹配：fileName + fileNamePinyin）
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            boolBuilder.must(m -> m.multiMatch(mm -> mm
                    .query(kw)
                    .fields("fileName^3", "fileNamePinyin^1")
                    .type(TextQueryType.BestFields)
                    .minimumShouldMatch("70%")
            ));
            // 前缀匹配补充（提高拼音首字母搜索命中率）
            boolBuilder.should(s -> s.matchPhrasePrefix(mp -> mp
                    .field("fileNamePinyin")
                    .query(kw)
                    .boost(2.0f)
            ));
        }

        // 文件类型过滤
        if (StringUtils.hasText(fileType)) {
            applyFileTypeFilter(boolBuilder, fileType);
        }

        // 构建 NativeQuery
        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(q -> q.bool(boolBuilder.build()))
                .withPageable(PageRequest.of(page, size))
                .withSort(s -> s.field(f -> f.field("createTime").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)));

        // 高亮
        if (StringUtils.hasText(keyword)) {
            queryBuilder.withHighlightQuery(
                    new HighlightQuery(
                            new Highlight(List.of(
                                    new HighlightField("fileName")
                            )),
                            FileSearchDocument.class
                    )
            );
        }

        NativeQuery query = queryBuilder.build();

        // 执行搜索
        SearchHits<FileSearchDocument> hits = elasticsearchOperations.search(query, FileSearchDocument.class);

        // 组装返回结果
        List<Map<String, Object>> items = new ArrayList<>();
        for (SearchHit<FileSearchDocument> hit : hits.getSearchHits()) {
            FileSearchDocument doc = hit.getContent();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", doc.getFileId());
            item.put("name", doc.getFileName());
            item.put("type", "file");
            item.put("sizeBytes", doc.getFileSize());
            item.put("mimeType", doc.getFileType());
            item.put("folderId", doc.getFolderId());
            item.put("createdAt", doc.getCreateTime());

            // 高亮结果
            Map<String, List<String>> highlights = hit.getHighlightFields();
            if (highlights.containsKey("fileName") && !highlights.get("fileName").isEmpty()) {
                item.put("highlightName", highlights.get("fileName").get(0));
            }
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("content", items);
        result.put("totalElements", hits.getTotalHits());
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    // ====================== 全量同步 ======================

    /**
     * 全量重建索引（管理接口，从 MySQL 读取所有数据写入 ES）
     */
    public void rebuildIndex(List<FileRecord> allFiles) {
        log.info("开始全量重建 ES 索引，文件数量: {}", allFiles.size());
        // 先清空索引
        try {
            elasticsearchOperations.indexOps(FileSearchDocument.class).delete();
        } catch (Exception ignored) {
        }
        // 创建索引
        elasticsearchOperations.indexOps(FileSearchDocument.class).create();
        // 批量写入
        int batchSize = 500;
        for (int i = 0; i < allFiles.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allFiles.size());
            indexFiles(allFiles.subList(i, end));
        }
        log.info("ES 全量索引重建完成");
    }

    // ====================== 工具方法 ======================

    private FileSearchDocument toDocument(FileRecord file) {
        FileSearchDocument doc = new FileSearchDocument();
        doc.setId(String.valueOf(file.getId()));
        doc.setFileId(file.getId());
        doc.setUserId(file.getUserId());
        doc.setFolderId(file.getFolderId());
        doc.setFileName(file.getFileName());
        doc.setFileNamePinyin(toPinyin(file.getFileName()));
        doc.setFileType(file.getFileType());
        doc.setFileSize(file.getFileSize());
        doc.setCreateTime(file.getCreateTime());
        doc.setStatus(file.getStatus());
        return doc;
    }

    /**
     * 将中文文件名转换为拼音（空格分隔）
     * 使用 Hutool 的 PinyinUtil（若未安装 pinyin4j 则简单返回原文）
     */
    private String toPinyin(String text) {
        if (!StringUtils.hasText(text)) return "";
        try {
            // 使用 Hutool 拼音转换
            return cn.hutool.extra.pinyin.PinyinUtil.getPinyin(text, " ");
        } catch (Exception e) {
            // 无 pinyin 库时返回原文（拼音搜索降级为普通文本搜索）
            return text;
        }
    }

    private void applyFileTypeFilter(BoolQuery.Builder builder, String fileType) {
        switch (fileType.toLowerCase()) {
            case "image" -> builder.filter(f -> f.wildcard(w -> w.field("fileType").value("image/*")));
            case "video" -> builder.filter(f -> f.wildcard(w -> w.field("fileType").value("video/*")));
            case "document" -> builder.filter(f -> f.bool(b -> b
                    .should(s -> s.wildcard(w -> w.field("fileType").value("application/pdf*")))
                    .should(s -> s.wildcard(w -> w.field("fileName").value("*.doc*")))
                    .should(s -> s.wildcard(w -> w.field("fileName").value("*.xls*")))
                    .should(s -> s.wildcard(w -> w.field("fileName").value("*.ppt*")))
                    .should(s -> s.wildcard(w -> w.field("fileName").value("*.txt")))
            ));
            case "archive" -> builder.filter(f -> f.bool(b -> b
                    .should(s -> s.wildcard(w -> w.field("fileName").value("*.zip")))
                    .should(s -> s.wildcard(w -> w.field("fileName").value("*.rar")))
                    .should(s -> s.wildcard(w -> w.field("fileName").value("*.7z")))
            ));
            default -> {
            }
        }
    }
}


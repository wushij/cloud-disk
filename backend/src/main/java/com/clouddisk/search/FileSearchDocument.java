package com.clouddisk.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "#{@esIndexName}")
@Setting(shards = 2, replicas = 0, settingPath = "es/file-search-settings.json")
public class FileSearchDocument {

    @Id
    private String id;  // fileId 的字符串形式

    @Field(type = FieldType.Long)
    private Long fileId;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Long)
    private Long folderId;

    /**
     * 文件名 —— IK 分词（全文搜索）
     * ik_max_word: 索引时最细粒度分词
     * ik_smart: 搜索时智能分词
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String fileName;

    /**
     * 文件名拼音 —— 拼音搜索
     * 使用 pinyin_analyzer（需要在 ES 中安装 elasticsearch-analysis-pinyin 插件）
     */
    @Field(type = FieldType.Text, analyzer = "pinyin_analyzer")
    private String fileNamePinyin;

    @Field(type = FieldType.Keyword)
    private String fileType;

    @Field(type = FieldType.Long)
    private Long fileSize;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime createTime;

    @Field(type = FieldType.Integer)
    private Integer status;
}

package com.clouddisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clouddisk.entity.FileChunk;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileChunkMapper extends BaseMapper<FileChunk> {
}

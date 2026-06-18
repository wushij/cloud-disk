package com.clouddisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clouddisk.entity.ShareRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShareMapper extends BaseMapper<ShareRecord> {
}

package com.clouddisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clouddisk.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}

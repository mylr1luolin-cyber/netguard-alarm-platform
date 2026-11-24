package com.netguard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.netguard.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户数据访问接口。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}

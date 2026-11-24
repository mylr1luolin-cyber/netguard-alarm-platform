package com.netguard.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.netguard.common.enums.RoleEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户表实体。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号，全局唯一 */
    private String username;

    /** BCrypt 加密后的密码 */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 角色：ADMIN / OPS */
    private RoleEnum role;

    /** 邮箱，用于接收告警邮件 */
    private String email;

    private String phone;

    /** 头像地址 */
    private String avatar;

    /** 1 启用、0 停用 */
    private Integer status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

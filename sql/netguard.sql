-- ============================================================
-- NetGuard 网络故障告警监控平台 —— 数据库初始化脚本
-- 数据库：MySQL 8.0    字符集：utf8mb4    排序规则：utf8mb4_general_ci
-- 使用方式：
--   mysql -u root -p < netguard.sql
-- 脚本内容：建库 + 9 张业务表 + 测试数据 + 默认账号 + 默认告警规则
-- 默认账号：admin / admin123（系统管理员）、ops / ops123（运维人员）
-- 说明：所有密码均使用 BCrypt(强度 10) 加密存储，禁止明文入库
-- ============================================================

CREATE DATABASE IF NOT EXISTS `netguard`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `netguard`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `inspect_detail`;
DROP TABLE IF EXISTS `inspect_log`;
DROP TABLE IF EXISTS `notify_record`;
DROP TABLE IF EXISTS `alarm_record`;
DROP TABLE IF EXISTS `daily_stat`;
DROP TABLE IF EXISTS `alarm_rule`;
DROP TABLE IF EXISTS `device`;
DROP TABLE IF EXISTS `device_type`;
DROP TABLE IF EXISTS `sys_user`;

SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- 1. 系统用户表：ADMIN 管理员 / OPS 运维人员
-- ------------------------------------------------------------
CREATE TABLE `sys_user`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `username`        VARCHAR(50)  NOT NULL COMMENT '登录账号',
    `password`        VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
    `real_name`       VARCHAR(50)  NOT NULL COMMENT '真实姓名',
    `role`            VARCHAR(20)  NOT NULL DEFAULT 'OPS' COMMENT '角色：ADMIN / OPS',
    `email`           VARCHAR(100)          DEFAULT NULL COMMENT '邮箱，用于接收告警邮件',
    `phone`           VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    `avatar`          VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 禁用',
    `last_login_time` DATETIME              DEFAULT NULL COMMENT '最近登录时间',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户表';

-- ------------------------------------------------------------
-- 2. 设备类型字典表
-- ------------------------------------------------------------
CREATE TABLE `device_type`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `type_name`   VARCHAR(50) NOT NULL COMMENT '类型名称',
    `type_code`   VARCHAR(50) NOT NULL COMMENT '类型编码（唯一）',
    `icon`        VARCHAR(50)          DEFAULT NULL COMMENT '图标（Emoji 或图标类名）',
    `core_flag`   TINYINT     NOT NULL DEFAULT 0 COMMENT '是否重点类别：1 是 0 否',
    `sort_order`  INT         NOT NULL DEFAULT 99 COMMENT '排序号，越小越靠前',
    `remark`      VARCHAR(255)         DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_code` (`type_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='设备类型字典表';

-- ------------------------------------------------------------
-- 3. 设备信息表
-- ------------------------------------------------------------
CREATE TABLE `device`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `device_code`      VARCHAR(50)  NOT NULL COMMENT '设备编号（唯一）',
    `device_name`      VARCHAR(100) NOT NULL COMMENT '设备名称',
    `ip_address`       VARCHAR(50)  NOT NULL COMMENT 'IP 地址',
    `device_type_id`   BIGINT       NOT NULL COMMENT '设备类型 ID',
    `monitor_port`     INT          NOT NULL DEFAULT 0 COMMENT '监控端口，0 表示使用 ICMP Ping',
    `location`         VARCHAR(120)          DEFAULT NULL COMMENT '部署位置（机房/机柜）',
    `core_flag`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否重点监控：1 是 0 否',
    `status`           VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN' COMMENT '状态：ONLINE / OFFLINE / UNKNOWN',
    `last_check_time`  DATETIME              DEFAULT NULL COMMENT '最近一次巡检时间',
    `last_online_time` DATETIME              DEFAULT NULL COMMENT '最近一次在线时间',
    `offline_count`    INT          NOT NULL DEFAULT 0 COMMENT '连续探测失败次数',
    `image_url`        VARCHAR(255)          DEFAULT NULL COMMENT '设备图片地址',
    `remark`           VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `create_by`        VARCHAR(50)           DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_code` (`device_code`),
    KEY `idx_ip_address` (`ip_address`),
    KEY `idx_status` (`status`),
    KEY `idx_type` (`device_type_id`),
    CONSTRAINT `fk_device_type` FOREIGN KEY (`device_type_id`) REFERENCES `device_type` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='设备信息表';

-- ------------------------------------------------------------
-- 4. 告警记录表
-- ------------------------------------------------------------
CREATE TABLE `alarm_record`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `device_id`         BIGINT       NOT NULL COMMENT '设备 ID',
    `device_name`       VARCHAR(100) NOT NULL COMMENT '设备名称（冗余，避免设备删除后丢失）',
    `device_ip`         VARCHAR(50)  NOT NULL COMMENT '设备 IP（冗余）',
    `alarm_type`        VARCHAR(20)  NOT NULL COMMENT '告警类型：OFFLINE 离线 / RECOVER 恢复',
    `alarm_level`       VARCHAR(20)  NOT NULL COMMENT '告警级别：URGENT 紧急 / NORMAL 普通',
    `alarm_status`      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / CONFIRMED / RECOVERED / CLOSED',
    `alarm_time`        DATETIME     NOT NULL COMMENT '告警产生时间',
    `confirm_user_id`   BIGINT                DEFAULT NULL COMMENT '确认人 ID',
    `confirm_user_name` VARCHAR(50)           DEFAULT NULL COMMENT '确认人姓名',
    `confirm_time`      DATETIME              DEFAULT NULL COMMENT '确认时间',
    `handle_remark`     VARCHAR(255)          DEFAULT NULL COMMENT '处理情况说明',
    `recover_time`      DATETIME              DEFAULT NULL COMMENT '设备恢复时间',
    `close_time`        DATETIME              DEFAULT NULL COMMENT '告警关闭时间',
    `duration_minutes`  BIGINT                DEFAULT NULL COMMENT '故障持续时长（分钟）',
    `notify_sent`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已发送通知：1 是 0 否',
    `remark`            VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_device` (`device_id`),
    KEY `idx_alarm_time` (`alarm_time`),
    KEY `idx_alarm_status` (`alarm_status`),
    KEY `idx_level` (`alarm_level`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='告警记录表';

-- ------------------------------------------------------------
-- 5. 通知记录表：页面通知 / 邮件通知
-- ------------------------------------------------------------
CREATE TABLE `notify_record`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `alarm_id`    BIGINT                DEFAULT NULL COMMENT '关联告警 ID',
    `channel`     VARCHAR(20)  NOT NULL COMMENT '渠道：PAGE 页面 / MAIL 邮件',
    `receiver`    VARCHAR(255)          DEFAULT NULL COMMENT '接收人（页面通知为角色描述，邮件为邮箱）',
    `title`       VARCHAR(200) NOT NULL COMMENT '通知标题',
    `content`     VARCHAR(1000)         DEFAULT NULL COMMENT '通知内容',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS / FAILED / SKIPPED',
    `error_msg`   VARCHAR(255)          DEFAULT NULL COMMENT '失败原因',
    `send_time`   DATETIME              DEFAULT NULL COMMENT '发送时间',
    `read_flag`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：1 已读 0 未读',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_alarm` (`alarm_id`),
    KEY `idx_read` (`read_flag`),
    KEY `idx_channel` (`channel`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='通知记录表';

-- ------------------------------------------------------------
-- 6. 巡检批次表
-- ------------------------------------------------------------
CREATE TABLE `inspect_log`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `trigger_type`  VARCHAR(20) NOT NULL DEFAULT 'AUTO' COMMENT '触发方式：AUTO 定时 / MANUAL 手动',
    `total_count`   INT         NOT NULL DEFAULT 0 COMMENT '设备总数',
    `online_count`  INT         NOT NULL DEFAULT 0 COMMENT '在线设备数',
    `offline_count` INT         NOT NULL DEFAULT 0 COMMENT '离线设备数',
    `change_count`  INT         NOT NULL DEFAULT 0 COMMENT '状态发生变化设备数',
    `start_time`    DATETIME    NOT NULL COMMENT '开始时间',
    `end_time`      DATETIME             DEFAULT NULL COMMENT '结束时间',
    `cost_ms`       BIGINT      NOT NULL DEFAULT 0 COMMENT '总耗时（毫秒）',
    `operator`      VARCHAR(50)          DEFAULT NULL COMMENT '执行人（定时任务为 system）',
    `remark`        VARCHAR(255)         DEFAULT NULL COMMENT '巡检参数说明',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_start_time` (`start_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='巡检批次表';

-- ------------------------------------------------------------
-- 7. 巡检明细表
-- ------------------------------------------------------------
CREATE TABLE `inspect_detail`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `log_id`      BIGINT      NOT NULL COMMENT '巡检批次 ID',
    `device_id`   BIGINT      NOT NULL COMMENT '设备 ID',
    `device_name` VARCHAR(100)         DEFAULT NULL COMMENT '设备名称（冗余）',
    `device_ip`   VARCHAR(50)          DEFAULT NULL COMMENT '设备 IP（冗余）',
    `probe_type`  VARCHAR(20)          DEFAULT NULL COMMENT '探测方式：TCP / ICMP',
    `result`      VARCHAR(20) NOT NULL COMMENT '探测结果：SUCCESS / FAILED',
    `latency_ms`  BIGINT               DEFAULT NULL COMMENT '探测耗时（毫秒）',
    `message`     VARCHAR(255)         DEFAULT NULL COMMENT '探测说明',
    `check_time`  DATETIME    NOT NULL COMMENT '探测时间',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_log` (`log_id`),
    KEY `idx_device` (`device_id`),
    CONSTRAINT `fk_detail_log` FOREIGN KEY (`log_id`) REFERENCES `inspect_log` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='巡检明细表';

-- ------------------------------------------------------------
-- 8. 告警规则表（单行配置）
-- ------------------------------------------------------------
CREATE TABLE `alarm_rule`
(
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `rule_name`           VARCHAR(100) NOT NULL COMMENT '规则名称',
    `inspect_interval`    INT         NOT NULL DEFAULT 5 COMMENT '巡检间隔（分钟）：5 / 10 / 30',
    `fail_threshold`      INT         NOT NULL DEFAULT 2 COMMENT '连续失败阈值（次）',
    `timeout_ms`          INT         NOT NULL DEFAULT 2000 COMMENT '单次探测超时（毫秒）',
    `urgent_enabled`      TINYINT     NOT NULL DEFAULT 1 COMMENT '重点设备是否产生紧急告警：1 是 0 否',
    `notify_page_enabled` TINYINT     NOT NULL DEFAULT 1 COMMENT '页面通知开关：1 开 0 关',
    `notify_mail_enabled` TINYINT     NOT NULL DEFAULT 0 COMMENT '邮件通知开关：1 开 0 关',
    `mail_receivers`      VARCHAR(255)         DEFAULT NULL COMMENT '邮件接收人，英文逗号分隔',
    `active_flag`         TINYINT     NOT NULL DEFAULT 1 COMMENT '巡检开关：1 启用 0 暂停',
    `remark`              VARCHAR(255)         DEFAULT NULL COMMENT '备注',
    `update_by`           VARCHAR(50)          DEFAULT NULL COMMENT '最后更新人',
    `create_time`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='告警规则配置表';

-- ------------------------------------------------------------
-- 9. 每日统计表（定时任务汇总）
-- ------------------------------------------------------------
CREATE TABLE `daily_stat`
(
    `id`                   BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `stat_date`            DATE     NOT NULL COMMENT '统计日期',
    `total_count`          INT      NOT NULL DEFAULT 0 COMMENT '告警总数',
    `urgent_count`         INT      NOT NULL DEFAULT 0 COMMENT '紧急告警数',
    `normal_count`         INT      NOT NULL DEFAULT 0 COMMENT '普通告警数',
    `recovered_count`      INT      NOT NULL DEFAULT 0 COMMENT '已恢复数',
    `avg_duration_minutes` BIGINT   NOT NULL DEFAULT 0 COMMENT '平均处理时长（分钟）',
    `create_time`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stat_date` (`stat_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='每日告警统计表';

-- ============================================================
-- 测试数据
-- ============================================================

-- 默认账号（密码：admin123 / ops123，BCrypt 加密）
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `role`, `email`, `phone`, `status`, `last_login_time`)
VALUES (1, 'admin', '$2a$10$15pzQZkkHNDW0IP0Ot7cWu5UgXiIBTFIAVM3yzXTkeiPloANswCPq', '系统管理员', 'ADMIN',
        'admin@netguard.com', '13800000001', 1, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
       (2, 'ops', '$2a$10$TmSsauPKn94UURPuYUZ..uueHiTmEnQr8fTlv2bsD6CYdZcAr5c0i', '张运维', 'OPS',
        'ops@netguard.com', '13800000002', 1, DATE_SUB(NOW(), INTERVAL 25 MINUTE)),
       (3, 'ops_li', '$2a$10$TmSsauPKn94UURPuYUZ..uueHiTmEnQr8fTlv2bsD6CYdZcAr5c0i', '李工', 'OPS',
        'li@netguard.com', '13800000003', 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
       (4, 'ops_wang', '$2a$10$TmSsauPKn94UURPuYUZ..uueHiTmEnQr8fTlv2bsD6CYdZcAr5c0i', '王工', 'OPS',
        'wang@netguard.com', '13800000004', 1, DATE_SUB(NOW(), INTERVAL 5 DAY)),
       (5, 'ops_zhao', '$2a$10$TmSsauPKn94UURPuYUZ..uueHiTmEnQr8fTlv2bsD6CYdZcAr5c0i', '赵工', 'OPS',
        'zhao@netguard.com', '13800000005', 0, NULL);

INSERT INTO `device_type` (`id`, `type_name`, `type_code`, `icon`, `core_flag`, `sort_order`, `remark`)
VALUES (1, '核心交换机', 'CORE_SWITCH', '🖧', 1, 1, '数据中心核心层交换设备'),
       (2, '接入交换机', 'ACCESS_SWITCH', '🔀', 0, 2, '各楼层接入层交换设备'),
       (3, '边界防火墙', 'FIREWALL', '🛡', 1, 3, '互联网出口安全设备'),
       (4, '应用服务器', 'APP_SERVER', '🖥', 1, 4, '部署业务系统的服务器'),
       (5, '无线控制器', 'WLC', '📶', 0, 5, '无线网络集中管理设备'),
       (6, '终端设备', 'TERMINAL', '🖨', 0, 6, '打印机、摄像头等终端'),
       (7, '存储设备', 'STORAGE', '💾', 0, 7, 'NAS / SAN 存储');

INSERT INTO `device` (`id`, `device_code`, `device_name`, `ip_address`, `device_type_id`, `monitor_port`, `location`,
                      `core_flag`, `status`, `last_check_time`, `last_online_time`, `offline_count`, `remark`, `create_by`)
VALUES (1, 'DEV-CORE-01', '核心交换机 CoreSW-01', '127.0.0.1', 1, 0, 'A 机房 A01 机柜', 1, 'ONLINE',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE), DATE_SUB(NOW(), INTERVAL 3 MINUTE), 0, '双电源冗余（本机演示指向回环地址）', 'admin'),
       (2, 'DEV-CORE-02', '核心交换机 CoreSW-02', '10.10.1.2', 1, 22, 'A 机房 A02 机柜', 1, 'OFFLINE',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE), DATE_SUB(NOW(), INTERVAL 2 HOUR), 3, '堆叠备机', 'admin'),
       (3, 'DEV-AGG-01', '汇聚交换机 AggSW-01', '127.0.0.2', 2, 0, 'A 机房 A05 机柜', 1, 'ONLINE',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE), DATE_SUB(NOW(), INTERVAL 3 MINUTE), 0, '本机演示指向回环地址', 'admin'),
       (4, 'DEV-ACC-11', '接入交换机 AccSW-11', '127.0.0.3', 2, 0, '3 楼弱电间', 0, 'ONLINE',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE), DATE_SUB(NOW(), INTERVAL 3 MINUTE), 0, '仅支持 Ping（本机演示指向回环地址）', 'admin'),
       (5, 'DEV-FW-01', '边界防火墙 FW-01', '127.0.0.1', 3, 8080, 'A 机房 A00 机柜', 1, 'ONLINE',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE), DATE_SUB(NOW(), INTERVAL 3 MINUTE), 0, '双机热备主设备（演示探测本机 8080 端口）', 'admin'),
       (6, 'DEV-APP-01', '应用服务器 App-01', '127.0.0.1', 4, 8081, 'B 机房 B01 机柜', 1, 'OFFLINE',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE), DATE_SUB(NOW(), INTERVAL 5 HOUR), 2, '本机演示：8081 端口未监听，用于展示离线告警', 'admin'),
       (7, 'DEV-DB-01', '数据库服务器 DB-01', '10.20.1.21', 4, 3306, 'B 机房 B02 机柜', 1, 'ONLINE',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE), DATE_SUB(NOW(), INTERVAL 3 MINUTE), 0, 'MySQL 主库', 'admin'),
       (8, 'DEV-WLC-01', '无线控制器 AC-01', '10.10.4.1', 5, 22, 'A 机房 A08 机柜', 0, 'ONLINE',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE), DATE_SUB(NOW(), INTERVAL 3 MINUTE), 0, NULL, 'admin'),
       (9, 'DEV-PRN-31', '3 楼打印机 PRN-31', '10.30.1.31', 6, 0, '3 楼办公区', 0, 'UNKNOWN',
        DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 0, '新录入，尚未巡检', 'admin'),
       (10, 'DEV-NVR-01', '视频监控 NVR-01', '10.30.1.41', 6, 8000, '1 楼安防室', 0, 'OFFLINE',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE), DATE_SUB(NOW(), INTERVAL 9 HOUR), 1, '摄像头录像主机', 'admin');

-- 告警记录：含待处理、已确认、已恢复等状态，覆盖紧急/普通级别
INSERT INTO `alarm_record` (`id`, `device_id`, `device_name`, `device_ip`, `alarm_type`, `alarm_level`,
                            `alarm_status`, `alarm_time`, `confirm_user_id`, `confirm_user_name`, `confirm_time`,
                            `handle_remark`, `recover_time`, `close_time`, `duration_minutes`, `notify_sent`)
VALUES (1, 2, '核心交换机 CoreSW-02', '10.10.1.2', 'OFFLINE', 'URGENT', 'PENDING',
        DATE_SUB(NOW(), INTERVAL 2 HOUR), NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1),
       (2, 6, '应用服务器 App-01', '127.0.0.1', 'OFFLINE', 'URGENT', 'CONFIRMED',
        DATE_SUB(NOW(), INTERVAL 5 HOUR), 2, '张运维', DATE_SUB(NOW(), INTERVAL 4 HOUR),
        '已联系机房值班人员检查电源模块，正在更换备件', NULL, NULL, NULL, 1),
       (3, 10, '视频监控 NVR-01', '10.30.1.41', 'OFFLINE', 'NORMAL', 'PENDING',
        DATE_SUB(NOW(), INTERVAL 9 HOUR), NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1),
       (4, 2, '核心交换机 CoreSW-02', '10.10.1.2', 'OFFLINE', 'URGENT', 'RECOVERED',
        DATE_SUB(NOW(), INTERVAL 2 DAY), 2, '张运维', DATE_SUB(NOW(), INTERVAL 47 HOUR),
        '重启交换机后恢复，已升级 IOS 补丁', DATE_SUB(NOW(), INTERVAL 46 HOUR),
        DATE_SUB(NOW(), INTERVAL 46 HOUR), 120, 1),
       (5, 6, '应用服务器 App-01', '127.0.0.1', 'OFFLINE', 'URGENT', 'RECOVERED',
        DATE_SUB(NOW(), INTERVAL 3 DAY), 3, '李工', DATE_SUB(NOW(), INTERVAL 71 HOUR),
        '磁盘阵列故障，更换硬盘并重建 RAID', DATE_SUB(NOW(), INTERVAL 68 HOUR),
        DATE_SUB(NOW(), INTERVAL 68 HOUR), 180, 1),
       (6, 4, '接入交换机 AccSW-11', '127.0.0.3', 'OFFLINE', 'NORMAL', 'RECOVERED',
        DATE_SUB(NOW(), INTERVAL 4 DAY), 2, '张运维', DATE_SUB(NOW(), INTERVAL 95 HOUR),
        '弱电间断电，恢复供电后自动上线', DATE_SUB(NOW(), INTERVAL 94 HOUR),
        DATE_SUB(NOW(), INTERVAL 94 HOUR), 60, 1),
       (7, 10, '视频监控 NVR-01', '10.30.1.41', 'OFFLINE', 'NORMAL', 'RECOVERED',
        DATE_SUB(NOW(), INTERVAL 6 DAY), 4, '王工', DATE_SUB(NOW(), INTERVAL 143 HOUR),
        '网线松动，重新压接水晶头后恢复', DATE_SUB(NOW(), INTERVAL 142 HOUR),
        DATE_SUB(NOW(), INTERVAL 142 HOUR), 85, 1),
       (8, 8, '无线控制器 AC-01', '10.10.4.1', 'OFFLINE', 'NORMAL', 'CLOSED',
        DATE_SUB(NOW(), INTERVAL 9 DAY), 3, '李工', DATE_SUB(NOW(), INTERVAL 215 HOUR),
        '设备重启维护，已在维护窗口内完成', DATE_SUB(NOW(), INTERVAL 214 HOUR),
        DATE_SUB(NOW(), INTERVAL 214 HOUR), 30, 0),
       (9, 2, '核心交换机 CoreSW-02', '10.10.1.2', 'RECOVER', 'URGENT', 'CLOSED',
        DATE_SUB(NOW(), INTERVAL 46 HOUR), NULL, NULL, NULL,
        '设备恢复在线，故障持续 2 小时 0 分钟', DATE_SUB(NOW(), INTERVAL 46 HOUR),
        DATE_SUB(NOW(), INTERVAL 46 HOUR), 120, 1),
       (10, 5, '边界防火墙 FW-01', '127.0.0.1', 'OFFLINE', 'URGENT', 'RECOVERED',
        DATE_SUB(NOW(), INTERVAL 12 DAY), 1, '系统管理员', DATE_SUB(NOW(), INTERVAL 287 HOUR),
        '策略升级导致会话中断，回滚配置后恢复', DATE_SUB(NOW(), INTERVAL 286 HOUR),
        DATE_SUB(NOW(), INTERVAL 286 HOUR), 45, 1),
       (11, 1, '核心交换机 CoreSW-01', '127.0.0.1', 'OFFLINE', 'URGENT', 'RECOVERED',
        DATE_SUB(NOW(), INTERVAL 18 DAY), 2, '张运维', DATE_SUB(NOW(), INTERVAL 431 HOUR),
        '电源模块故障，更换后恢复', DATE_SUB(NOW(), INTERVAL 430 HOUR),
        DATE_SUB(NOW(), INTERVAL 430 HOUR), 75, 1),
       (12, 7, '数据库服务器 DB-01', '10.20.1.21', 'OFFLINE', 'URGENT', 'RECOVERED',
        DATE_SUB(NOW(), INTERVAL 25 DAY), 3, '李工', DATE_SUB(NOW(), INTERVAL 599 HOUR),
        '主从切换演练，切换期间短暂不可用', DATE_SUB(NOW(), INTERVAL 598 HOUR),
        DATE_SUB(NOW(), INTERVAL 598 HOUR), 20, 1),
       (13, 4, '接入交换机 AccSW-11', '127.0.0.3', 'OFFLINE', 'NORMAL', 'RECOVERED',
        DATE_SUB(NOW(), INTERVAL 33 DAY), 4, '王工', DATE_SUB(NOW(), INTERVAL 791 HOUR),
        '弱电间空调故障导致设备过热关机', DATE_SUB(NOW(), INTERVAL 789 HOUR),
        DATE_SUB(NOW(), INTERVAL 789 HOUR), 110, 1),
       (14, 10, '视频监控 NVR-01', '10.30.1.41', 'OFFLINE', 'NORMAL', 'RECOVERED',
        DATE_SUB(NOW(), INTERVAL 40 DAY), 2, '张运维', DATE_SUB(NOW(), INTERVAL 959 HOUR),
        '录像主机死机，重启后恢复', DATE_SUB(NOW(), INTERVAL 958 HOUR),
        DATE_SUB(NOW(), INTERVAL 958 HOUR), 55, 1);

-- 通知记录：页面通知与邮件通知
INSERT INTO `notify_record` (`id`, `alarm_id`, `channel`, `receiver`, `title`, `content`, `status`, `error_msg`,
                             `send_time`, `read_flag`)
VALUES (1, 1, 'PAGE', '全体运维人员', '【紧急】设备离线告警：核心交换机 CoreSW-02',
        CONCAT('设备 核心交换机 CoreSW-02（10.10.1.2）于 ',
               DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 2 HOUR), '%Y-%m-%d %H:%i:%s'),
               ' 探测失败，已判定离线，请及时处理。'),
        'SUCCESS', NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR), 0),
       (2, 1, 'MAIL', 'ops@netguard.com', '【紧急】设备离线告警：核心交换机 CoreSW-02',
        '设备 CoreSW-02 离线，请尽快检查。', 'SUCCESS', NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR), 0),
       (3, 2, 'PAGE', '全体运维人员', '【紧急】设备离线告警：应用服务器 App-01',
        '设备 应用服务器 App-01（10.20.1.11）探测失败，已判定离线。', 'SUCCESS', NULL,
        DATE_SUB(NOW(), INTERVAL 5 HOUR), 1),
       (4, 2, 'MAIL', 'ops@netguard.com', '【紧急】设备离线告警：应用服务器 App-01',
        '设备 App-01 离线，请联系机房处理。', 'SUCCESS', NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR), 1),
       (5, 3, 'PAGE', '全体运维人员', '设备离线告警：视频监控 NVR-01',
        '设备 视频监控 NVR-01（10.30.1.41）探测失败，已判定离线。', 'SUCCESS', NULL,
        DATE_SUB(NOW(), INTERVAL 9 HOUR), 0),
       (6, 4, 'PAGE', '全体运维人员', '设备恢复通知：核心交换机 CoreSW-02',
        '设备 CoreSW-02 已恢复在线，故障持续 2 小时 0 分钟。', 'SUCCESS', NULL,
        DATE_SUB(NOW(), INTERVAL 46 HOUR), 1),
       (7, 5, 'MAIL', 'li@netguard.com', '【紧急】设备离线告警：应用服务器 App-01',
        '设备 App-01 离线。', 'SUCCESS', NULL, DATE_SUB(NOW(), INTERVAL 71 HOUR), 1),
       (8, 7, 'MAIL', 'wang@netguard.com', '设备离线告警：视频监控 NVR-01',
        '设备 NVR-01 离线。', 'FAILED', '邮件通道未开启或发送失败，请检查 SMTP 配置',
        DATE_SUB(NOW(), INTERVAL 6 DAY), 1),
       (9, 8, 'PAGE', '全体运维人员', '设备离线告警：无线控制器 AC-01',
        '设备 AC-01 探测失败。', 'SUCCESS', NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), 1),
       (10, 14, 'MAIL', 'zhao@netguard.com', '设备离线告警：视频监控 NVR-01',
        '设备 NVR-01 离线。', 'SKIPPED', '未配置收件人邮箱', DATE_SUB(NOW(), INTERVAL 40 DAY), 1);

-- 巡检批次
INSERT INTO `inspect_log` (`id`, `trigger_type`, `total_count`, `online_count`, `offline_count`, `change_count`,
                           `start_time`, `end_time`, `cost_ms`, `operator`, `remark`)
VALUES (1, 'AUTO', 10, 7, 3, 0, DATE_SUB(NOW(), INTERVAL 3 MINUTE), DATE_SUB(NOW(), INTERVAL 3 MINUTE),
        2840, 'system', '间隔 5 分钟，失败阈值 2 次，超时 2000 ms'),
       (2, 'AUTO', 10, 7, 3, 1, DATE_SUB(NOW(), INTERVAL 8 MINUTE), DATE_SUB(NOW(), INTERVAL 8 MINUTE),
        3120, 'system', '间隔 5 分钟，失败阈值 2 次，超时 2000 ms'),
       (3, 'MANUAL', 10, 8, 2, 2, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR),
        2560, 'admin', '间隔 5 分钟，失败阈值 2 次，超时 2000 ms'),
       (4, 'AUTO', 9, 8, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY),
        2340, 'system', '间隔 5 分钟，失败阈值 2 次，超时 2000 ms'),
       (5, 'AUTO', 9, 9, 0, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY),
        1980, 'system', '间隔 10 分钟，失败阈值 2 次，超时 2000 ms'),
       (6, 'MANUAL', 8, 6, 2, 2, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY),
        2760, 'ops', '间隔 10 分钟，失败阈值 3 次，超时 3000 ms');

-- 巡检明细（批次 1 与批次 3 的探测结果）
INSERT INTO `inspect_detail` (`log_id`, `device_id`, `device_name`, `device_ip`, `probe_type`, `result`, `latency_ms`,
                              `message`, `check_time`)
VALUES (1, 1, '核心交换机 CoreSW-01', '127.0.0.1', 'ICMP', 'SUCCESS', 3, 'ICMP 可达',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE)),
       (1, 2, '核心交换机 CoreSW-02', '10.10.1.2', 'TCP', 'FAILED', 2001, 'Connect timed out',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE)),
       (1, 3, '汇聚交换机 AggSW-01', '127.0.0.2', 'ICMP', 'SUCCESS', 3, 'ICMP 可达',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE)),
       (1, 4, '接入交换机 AccSW-11', '127.0.0.3', 'ICMP', 'SUCCESS', 4, 'ICMP 可达',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE)),
       (1, 5, '边界防火墙 FW-01', '127.0.0.1', 'TCP', 'SUCCESS', 15, 'TCP 端口 8080 可连接',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE)),
       (1, 6, '应用服务器 App-01', '127.0.0.1', 'TCP', 'FAILED', 2003, 'Connect timed out',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE)),
       (1, 7, '数据库服务器 DB-01', '10.20.1.21', 'TCP', 'SUCCESS', 9, 'TCP 端口 3306 可连接',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE)),
       (1, 8, '无线控制器 AC-01', '10.10.4.1', 'TCP', 'SUCCESS', 11, 'TCP 端口 22 可连接',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE)),
       (1, 9, '3 楼打印机 PRN-31', '10.30.1.31', 'ICMP', 'SUCCESS', 6, 'ICMP 可达',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE)),
       (1, 10, '视频监控 NVR-01', '10.30.1.41', 'TCP', 'FAILED', 2000, 'Connect timed out',
        DATE_SUB(NOW(), INTERVAL 3 MINUTE)),
       (3, 1, '核心交换机 CoreSW-01', '127.0.0.1', 'ICMP', 'SUCCESS', 3, 'ICMP 可达',
        DATE_SUB(NOW(), INTERVAL 2 HOUR)),
       (3, 2, '核心交换机 CoreSW-02', '10.10.1.2', 'TCP', 'FAILED', 2002, 'Connect timed out',
        DATE_SUB(NOW(), INTERVAL 2 HOUR)),
       (3, 3, '汇聚交换机 AggSW-01', '127.0.0.2', 'ICMP', 'SUCCESS', 3, 'ICMP 可达',
        DATE_SUB(NOW(), INTERVAL 2 HOUR)),
       (3, 5, '边界防火墙 FW-01', '127.0.0.1', 'TCP', 'SUCCESS', 13, 'TCP 端口 8080 可连接',
        DATE_SUB(NOW(), INTERVAL 2 HOUR)),
       (3, 6, '应用服务器 App-01', '127.0.0.1', 'TCP', 'FAILED', 2001, 'Connect timed out',
        DATE_SUB(NOW(), INTERVAL 2 HOUR)),
       (3, 7, '数据库服务器 DB-01', '10.20.1.21', 'TCP', 'SUCCESS', 8, 'TCP 端口 3306 可连接',
        DATE_SUB(NOW(), INTERVAL 2 HOUR));

-- 告警规则（单行配置）
INSERT INTO `alarm_rule` (`id`, `rule_name`, `inspect_interval`, `fail_threshold`, `timeout_ms`, `urgent_enabled`,
                          `notify_page_enabled`, `notify_mail_enabled`, `mail_receivers`, `active_flag`, `remark`,
                          `update_by`)
VALUES (1, '默认告警规则', 5, 2, 2000, 1, 1, 0, 'ops@netguard.com', 1,
        '每 5 分钟巡检全部设备，连续 2 次失败判定离线，重点设备离线产生紧急告警', 'admin');

-- 每日统计（近 10 天）
INSERT INTO `daily_stat` (`stat_date`, `total_count`, `urgent_count`, `normal_count`, `recovered_count`,
                          `avg_duration_minutes`)
VALUES (DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3, 2, 1, 3, 88),
       (DATE_SUB(CURDATE(), INTERVAL 2 DAY), 2, 2, 0, 2, 120),
       (DATE_SUB(CURDATE(), INTERVAL 3 DAY), 1, 1, 0, 1, 180),
       (DATE_SUB(CURDATE(), INTERVAL 4 DAY), 1, 0, 1, 1, 60),
       (DATE_SUB(CURDATE(), INTERVAL 5 DAY), 0, 0, 0, 0, 0),
       (DATE_SUB(CURDATE(), INTERVAL 6 DAY), 1, 0, 1, 1, 85),
       (DATE_SUB(CURDATE(), INTERVAL 7 DAY), 0, 0, 0, 0, 0),
       (DATE_SUB(CURDATE(), INTERVAL 8 DAY), 2, 1, 1, 2, 52),
       (DATE_SUB(CURDATE(), INTERVAL 9 DAY), 1, 0, 1, 1, 30),
       (DATE_SUB(CURDATE(), INTERVAL 10 DAY), 0, 0, 0, 0, 0);

-- ============================================================
-- 初始化完成
-- 访问地址：http://localhost:8080/login
-- 管理员：admin / admin123        运维人员：ops / ops123
-- ============================================================

-- ============================================================
-- NetGuard 维护脚本：重置账号密码 / 检查账号表
-- 用途：换机器后登录提示"账号或密码错误"时，先确认账号数据是否存在，
--       再按需把密码重置成默认值。
-- 执行：mysql -u root -p123456 --default-character-set=utf8mb4 < sql/维护-重置管理员密码.sql
-- ============================================================

USE netguard;

-- 1) 先看有没有账号、角色和状态是否正常（status=1 才能登录）
SELECT id, username, real_name, role, status, email, LEFT(password, 7) AS pwd_prefix
FROM sys_user
ORDER BY id;

-- 2) 把 admin 密码重置为 admin123（BCrypt 密文，与 sql/netguard.sql 中的一致）
--    如果没有 admin 这一行，先用下面的 INSERT 建号再执行 UPDATE。
INSERT INTO sys_user (username, password, real_name, role, email, status, create_time, update_time)
SELECT 'admin', '$2a$10$15pzQZkkHNDW0IP0Ot7cWu5UgXiIBTFIAVM3yzXTkeiPloANswCPq', '系统管理员', 'ADMIN', 'admin@netguard.com', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

UPDATE sys_user
SET password = '$2a$10$15pzQZkkHNDW0IP0Ot7cWu5UgXiIBTFIAVM3yzXTkeiPloANswCPq',
    status = 1
WHERE username = 'admin';

-- 3) 运维账号 ops 密码重置为 ops123（如已存在）
UPDATE sys_user
SET password = '$2a$10$TmSsauPKn94UURPuYUZ..uueHiTmEnQr8fTlv2bsD6CYdZcAr5c0i',
    status = 1
WHERE username = 'ops';

-- 4) 确认结果（password 列只显示前 7 位）
SELECT username, role, status, LEFT(password, 7) AS pwd_prefix FROM sys_user ORDER BY id;

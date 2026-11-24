package com.netguard.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.netguard.common.enums.RoleEnum;
import com.netguard.entity.SysUser;
import com.netguard.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时的账号自检。
 *
 * <p>换一台机器、或者只建了库却没导入 {@code sql/netguard.sql} 时，{@code sys_user} 可能是空表，
 * 这时任何账号都登不进去，页面上只会显示"账号或密码错误"，很难判断原因。这里在启动完成后
 * 检查一次：表存在但没有 admin 账号时，自动补建默认管理员（并在日志里明确写出来），
 * 让"新环境跑起来"不再卡在登录这一步。</p>
 *
 * <p>表本身不存在（没导入脚本）时不猜测、不建表，只打印一条可操作的错误提示。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAccountInitializer implements ApplicationRunner {

    /** 默认管理员账号，与 sql/netguard.sql 中的初始化数据保持一致 */
    private static final String DEFAULT_ADMIN = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    /** 默认运维账号 */
    private static final String DEFAULT_OPS = "ops";
    private static final String DEFAULT_OPS_PASSWORD = "ops123";

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    /** 启动参数 --app.reset-admin=true 时无条件重置默认账号密码，便于服务器上一键恢复登录 */
    @Value("${app.reset-admin:false}")
    private boolean resetRequested;

    @Override
    public void run(ApplicationArguments args) {
        try {
            long total = sysUserMapper.selectCount(null);
            boolean hasAdmin = sysUserMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, DEFAULT_ADMIN)) > 0;
            log.info("账号自检：sys_user 现有 {} 个账号，admin 账号存在={}", total, hasAdmin);

            // 启动参数里带 --app.reset-admin=true 时，无条件把默认账号密码重置为初始值。
            // 作用：服务器上没有 mysql 客户端、也懒得敲 SQL 时，用它一步恢复登录。
            if (resetRequested) {
                resetTo(DEFAULT_ADMIN, DEFAULT_ADMIN_PASSWORD, "系统管理员", RoleEnum.ADMIN, "admin@netguard.com");
                resetTo(DEFAULT_OPS, DEFAULT_OPS_PASSWORD, "张运维", RoleEnum.OPS, "ops@netguard.com");
                log.warn("已按 --app.reset-admin=true 重置默认账号：{}/{}、{}/{}",
                        DEFAULT_ADMIN, DEFAULT_ADMIN_PASSWORD, DEFAULT_OPS, DEFAULT_OPS_PASSWORD);
                return;
            }

            if (hasAdmin) {
                // 账号在但密文不合法（最常见的原因：把带 $ 的 BCrypt 串粘到 bash 双引号里，
                // $2a$10$... 被当成变量展开，库里只剩半截），这种情况登录必然失败，直接修好。
                repairBrokenHash(DEFAULT_ADMIN, DEFAULT_ADMIN_PASSWORD);
                repairBrokenHash(DEFAULT_OPS, DEFAULT_OPS_PASSWORD);
                warnDuplicates(DEFAULT_ADMIN);
                warnDuplicates(DEFAULT_OPS);
                return;
            }

            createIfAbsent(DEFAULT_ADMIN, DEFAULT_ADMIN_PASSWORD, "系统管理员", RoleEnum.ADMIN, "admin@netguard.com");
            createIfAbsent(DEFAULT_OPS, DEFAULT_OPS_PASSWORD, "张运维", RoleEnum.OPS, "ops@netguard.com");
            log.warn("数据库中缺少 admin 账号，已自动创建默认账号 {}/{}（运维账号 {}/{}），登录后请立即修改密码",
                    DEFAULT_ADMIN, DEFAULT_ADMIN_PASSWORD, DEFAULT_OPS, DEFAULT_OPS_PASSWORD);
        } catch (Exception e) {
            log.error("账号自检失败：{}。若这是新环境，请先导入数据库脚本 sql/netguard.sql（库名 netguard）后再启动", e.getMessage());
        }
    }

    /**
     * 检查账号的密码字段是否是合法的 BCrypt 密文；不是就重置成默认密码并打日志。
     *
     * <p>BCrypt 密文形如 {@code $2a$10$<53 位>}，总长 60。若有人在 shell 里用双引号执行
     * INSERT，{@code $2a$10$...} 会被变量展开成空串或残段，库里存的就是无效字符串，
     * 表现为"账号存在、密码也输对了，但一直提示账号或密码错误"。</p>
     */
    private void repairBrokenHash(String username, String rawPassword) {
        SysUser user = selectFirst(username);
        if (user == null) {
            return;
        }
        String hash = user.getPassword();
        boolean looksValid = hash != null && hash.length() == 60
                && (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
        if (looksValid) {
            log.debug("账号 {} 的密码密文格式正常（长度 {}）", username, hash.length());
            return;
        }
        user.setPassword(passwordEncoder.encode(rawPassword));
        if (user.getStatus() == null || user.getStatus() != 1) {
            user.setStatus(1);
        }
        sysUserMapper.updateById(user);
        log.warn("账号 {} 的密码字段不是合法的 BCrypt 密文（长度 {}，内容形如 \"{}\"），"
                        + "通常是因为把 SQL 粘进 shell 双引号导致 $ 被展开；已重置为默认密码 {}",
                username, hash == null ? 0 : hash.length(), mask(hash), rawPassword);
    }

    /**
     * 检测同名账号：{@code sys_user} 若有两条 admin，登录会命中 id 最小的那条，
     * 另一条的密码改了不会生效，用户会一直以为"改了没用"。</p>
     */
    private void warnDuplicates(String username) {
        long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (count > 1) {
            SysUser first = selectFirst(username);
            long keepId = first == null ? 0L : first.getId();
            log.warn("sys_user 中存在 {} 条同名账号 \"{}\"，登录使用 id={} 的那条；"
                            + "多余记录请删除：DELETE FROM sys_user WHERE username='{}' AND id<>{}",
                    count, username, keepId, username, keepId);
        }
    }

    /**
     * 按用户名取 id 最小的一条记录（避免 selectOne 在重复数据上抛异常）。
     */
    private SysUser selectFirst(String username) {
        List<SysUser> list = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .orderByAsc(SysUser::getId));
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    /**
     * 无条件重置（用于 {@code --app.reset-admin=true}）：有则改密码并启用，无则新建。
     */
    private void resetTo(String username, String rawPassword, String realName, RoleEnum role, String email) {
        SysUser user = selectFirst(username);
        if (user == null) {
            createIfAbsent(username, rawPassword, realName, role, email);
            return;
        }
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setStatus(1);
        sysUserMapper.updateById(user);
    }

    /**
     * 日志里只展示密文的前几位，避免把完整哈希写进日志。
     */
    private String mask(String hash) {
        if (hash == null || hash.isEmpty()) {
            return "";
        }
        return hash.length() <= 10 ? hash : hash.substring(0, 10) + "...";
    }

    /**
     * 按用户名补建账号，已存在则跳过（不会覆盖用户改过的密码）。
     */
    private void createIfAbsent(String username, String rawPassword, String realName, RoleEnum role, String email) {
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) > 0) {
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRealName(realName);
        user.setRole(role);
        user.setEmail(email);
        user.setStatus(1);
        sysUserMapper.insert(user);
    }
}

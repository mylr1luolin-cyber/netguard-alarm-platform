package com.netguard.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.netguard.entity.SysUser;
import com.netguard.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.stereotype.Component;

/**
 * 登录失败日志。
 *
 * <p>Spring Security 默认失败只做一个 redirect，日志里什么都没有，用户看到的就是一句
 * "账号或密码错误"，到底是账号不存在、密码不对，还是密码字段是坏数据，完全无法区分。
 * 这里在认证失败时补一条 WARN：账号是否存在、密码密文长度是多少，方便现场排查。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginFailureListener {

    private final SysUserMapper sysUserMapper;

    /**
     * 账号密码不匹配：区分"账号不存在"和"密文异常"。
     */
    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String username = String.valueOf(event.getAuthentication().getPrincipal());
        SysUser user = findUser(username);
        if (user == null) {
            log.warn("登录失败：账号 \"{}\" 在 sys_user 中不存在（检查连的库是否正确、是否导入 sql/netguard.sql）", username);
            return;
        }
        String hash = user.getPassword();
        int len = hash == null ? 0 : hash.length();
        if (len != 60) {
            log.warn("登录失败：账号 \"{}\" 存在，但 password 字段长度为 {}（正常应为 60），"
                    + "属于坏数据；可执行 sql/维护-重置管理员密码.sql 或用 --app.reset-admin=true 启动修复", username, len);
        } else {
            log.warn("登录失败：账号 \"{}\" 存在且密文长度正常，但密码不匹配（确认输入的是 admin123，注意大小写与空格）", username);
        }
    }

    /**
     * 账号被停用。
     */
    @EventListener
    public void onDisabled(AuthenticationFailureDisabledEvent event) {
        log.warn("登录失败：账号 \"{}\" 已被停用（status=0），请到数据库里把 status 改成 1",
                event.getAuthentication().getPrincipal());
    }

    /**
     * 其它认证异常（例如账号被锁定）统一记一条。
     */
    @EventListener
    public void onOtherFailure(AbstractAuthenticationFailureEvent event) {
        if (event instanceof AuthenticationFailureBadCredentialsEvent
                || event instanceof AuthenticationFailureDisabledEvent) {
            return;
        }
        log.warn("登录失败：账号 \"{}\"，原因 {}", event.getAuthentication().getPrincipal(),
                event.getException().getMessage());
    }

    private SysUser findUser(String username) {
        try {
            return sysUserMapper.selectOne(
                    Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username).last("LIMIT 1"));
        } catch (Exception e) {
            return null;
        }
    }
}

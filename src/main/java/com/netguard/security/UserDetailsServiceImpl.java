package com.netguard.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.netguard.entity.SysUser;
import com.netguard.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security 用户加载服务，从 sys_user 表按账号读取用户。
 *
 * <p>这里刻意不用 {@code selectOne}：如果表里出现同名账号（手工补数据、脚本重复执行都可能造成），
 * {@code selectOne} 会抛 TooManyResultsException，Spring Security 只会把它当成"认证失败"，
 * 于是密码明明正确也一直提示"账号或密码错误"，很难查。改为取 id 最小的那条并打一条 WARN。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<SysUser> users = sysUserMapper.selectList(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, username)
                        .orderByAsc(SysUser::getId));
        if (users == null || users.isEmpty()) {
            throw new UsernameNotFoundException("账号或密码错误");
        }
        if (users.size() > 1) {
            log.warn("sys_user 中存在 {} 条同名账号 \"{}\"（id={}），登录按 id 最小的一条校验；"
                            + "建议删除多余记录：DELETE FROM sys_user WHERE username='{}' AND id<>{}",
                    users.size(), username, users.get(0).getId(), username, users.get(0).getId());
        }
        SysUser user = users.get(0);
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new DisabledException("账号已被停用，请联系管理员");
        }
        return new LoginUser(user);
    }
}

package com.netguard.security;

import com.netguard.entity.SysUser;
import com.netguard.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 登录成功处理器：记录最近登录时间，并跳转到原访问页面或首页看板。
 */
@Slf4j
@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final SysUserMapper sysUserMapper;

    public LoginSuccessHandler(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
        setDefaultTargetUrl("/dashboard");
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof LoginUser) {
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();
            SysUser update = new SysUser();
            update.setId(loginUser.getId());
            update.setLastLoginTime(LocalDateTime.now());
            try {
                sysUserMapper.updateById(update);
            } catch (Exception e) {
                // 登录时间更新失败不影响登录本身
                log.warn("更新最近登录时间失败：{}", e.getMessage());
            }
            log.info("用户 [{}] 登录成功，角色 {}", loginUser.getUsername(), loginUser.getRole());
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}

package com.netguard.security;

import com.netguard.common.enums.RoleEnum;
import com.netguard.entity.SysUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security 登录用户，包装数据库实体并暴露角色、邮箱等业务字段。
 */
@Getter
public class LoginUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final SysUser user;

    public LoginUser(SysUser user) {
        this.user = user;
    }

    public Long getId() {
        return user.getId();
    }

    public String getRealName() {
        return user.getRealName();
    }

    public RoleEnum getRole() {
        return user.getRole();
    }

    public String getEmail() {
        return user.getEmail();
    }

    public String getAvatar() {
        return user.getAvatar();
    }

    public boolean isAdmin() {
        return RoleEnum.ADMIN == user.getRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (user.getRole() == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() != null && user.getStatus() == 1;
    }
}

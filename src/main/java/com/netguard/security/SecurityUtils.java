package com.netguard.security;

import com.netguard.common.enums.RoleEnum;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具类，供 Service 层记录操作人、Controller 层取用户信息。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户，未登录返回 null。
     */
    public static LoginUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser) {
            return (LoginUser) principal;
        }
        return null;
    }

    public static Long currentUserId() {
        LoginUser loginUser = current();
        return loginUser == null ? null : loginUser.getId();
    }

    public static String currentUsername() {
        LoginUser loginUser = current();
        return loginUser == null ? "system" : loginUser.getUsername();
    }

    public static String currentRealName() {
        LoginUser loginUser = current();
        return loginUser == null ? "系统" : loginUser.getRealName();
    }

    public static boolean isAdmin() {
        LoginUser loginUser = current();
        return loginUser != null && loginUser.getRole() == RoleEnum.ADMIN;
    }
}

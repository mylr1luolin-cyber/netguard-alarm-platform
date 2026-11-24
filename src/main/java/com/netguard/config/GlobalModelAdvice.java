package com.netguard.config;

import com.netguard.service.AlarmService;
import com.netguard.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 全局模型增强：为侧边栏提供待处理告警数与未读消息数角标。
 *
 * <p>仅在用户已登录时查询，未登录或查询异常时静默跳过，避免影响登录页与错误页。</p>
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final AlarmService alarmService;
    private final NotifyService notifyService;

    /**
     * 注入全局角标数据。
     */
    @ModelAttribute
    public void injectBadges(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return;
        }
        try {
            model.addAttribute("globalPending", alarmService.pendingCount());
            model.addAttribute("globalUnread", notifyService.unreadCount());
        } catch (Exception e) {
            log.debug("加载全局角标数据失败: {}", e.getMessage());
        }
    }
}

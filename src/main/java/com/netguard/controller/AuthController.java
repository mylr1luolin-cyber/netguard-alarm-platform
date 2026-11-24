package com.netguard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * 登录与错误页控制器。
 */
@Controller
public class AuthController {

    /**
     * 登录页：已登录用户直接进入看板。
     */
    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        @RequestParam(value = "expired", required = false) String expired,
                        HttpServletRequest request) {
        if (error != null) {
            request.setAttribute("errorMsg", "账号或密码错误，请重新输入");
        } else if (logout != null) {
            request.setAttribute("successMsg", "您已安全退出登录");
        } else if (expired != null) {
            request.setAttribute("errorMsg", "账号已在其它地方登录，当前会话已失效");
        }
        return "login";
    }

    /**
     * 根路径跳转到监控看板。
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    /**
     * 无权限提示页。
     */
    @GetMapping("/403")
    public String forbidden() {
        return "error/403";
    }
}

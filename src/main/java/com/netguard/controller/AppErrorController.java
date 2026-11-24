package com.netguard.controller;

import com.netguard.common.Result;
import com.netguard.utils.JsonUtils;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 统一错误页处理。
 *
 * <p>目的有两个：</p>
 * <ol>
 *   <li>用户访问一个不存在的地址时，不要停在"404 / 页面不存在"上，而是直接送回登录页
 *       （未登录）或看板（已登录）——地址输错、链接过期都不会卡住；</li>
 *   <li>接口（{@code /api/**} 或 Accept 不是 HTML）保留 JSON 错误体，方便前端与 curl 调试。</li>
 * </ol>
 */
@Controller
public class AppErrorController implements ErrorController {

    /**
     * 接管 Spring Boot 默认的 /error。
     */
    @RequestMapping("/error")
    public ModelAndView handleError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int status = resolveStatus(request);
        String uri = resolveUri(request);
        String message = resolveMessage(request, status);

        // 接口请求：保持 JSON，别把 HTML 页面丢给 fetch
        if (!wantsHtml(request) || (uri != null && uri.startsWith("/api/"))) {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(JsonUtils.toJson(Result.fail(status, message)));
            return null;
        }

        // 页面请求里的 404：直接送回入口，不打断用户
        if (status == HttpStatus.NOT_FOUND.value()) {
            response.setStatus(HttpServletResponse.SC_FOUND);
            return new ModelAndView("redirect:/dashboard");
        }

        ModelAndView mv = new ModelAndView(status == HttpStatus.FORBIDDEN.value() ? "error/403" : "error/500");
        mv.addObject("path", uri);
        mv.addObject("message", message);
        mv.setStatus(HttpStatus.valueOf(status));
        return mv;
    }

    private int resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer) {
            int v = (Integer) code;
            return v >= 400 && v <= 599 ? v : 500;
        }
        return 500;
    }

    private String resolveUri(HttpServletRequest request) {
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return uri == null ? null : uri.toString();
    }

    private String resolveMessage(HttpServletRequest request, int status) {
        Object msg = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        if (msg != null && !msg.toString().isEmpty() && !"No message available".equals(msg.toString())) {
            return msg.toString();
        }
        switch (status) {
            case 403:
                return "当前账号没有访问该功能的权限，请联系系统管理员。";
            case 404:
                return "请求的地址不存在，可能链接已过期或输入有误。";
            default:
                return "系统处理请求时发生异常，请稍后重试；若持续出现请查看 logs/netguard-error.log。";
        }
    }

    /**
     * 浏览器（Accept 含 text/html）才回页面，curl / fetch 默认拿到 JSON。
     */
    private boolean wantsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }
}

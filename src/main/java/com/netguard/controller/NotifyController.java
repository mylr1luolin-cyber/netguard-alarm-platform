package com.netguard.controller;

import com.netguard.query.NotifyQuery;
import com.netguard.service.NotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 消息通知控制器：页面消息列表与已读处理。
 */
@Controller
@RequestMapping("/notices")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;

    /**
     * 消息列表。
     */
    @GetMapping
    public String list(@ModelAttribute("query") NotifyQuery query, Model model) {
        model.addAttribute("page", notifyService.page(query));
        model.addAttribute("unreadCount", notifyService.unreadCount());
        return "notice/list";
    }

    /**
     * 单条标记已读。
     */
    @PostMapping("/{id}/read")
    public String markRead(@PathVariable Long id, RedirectAttributes redirect) {
        notifyService.markRead(id);
        redirect.addFlashAttribute("msg", "已标记为已读");
        redirect.addFlashAttribute("msgType", "success");
        return "redirect:/notices";
    }

    /**
     * 全部标记已读。
     */
    @PostMapping("/read-all")
    public String markAllRead(RedirectAttributes redirect) {
        notifyService.markAllRead();
        redirect.addFlashAttribute("msg", "全部消息已标记为已读");
        redirect.addFlashAttribute("msgType", "success");
        return "redirect:/notices";
    }
}
